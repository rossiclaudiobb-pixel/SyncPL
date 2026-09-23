package com.example.myplaylistsync.network

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.myplaylistsync.MainActivity
import com.example.myplaylistsync.R
import com.example.myplaylistsync.data.Playlist
import com.example.myplaylistsync.data.SettingsRepository
import com.example.myplaylistsync.data.SyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private lateinit var syncManager: SyncManager
    private lateinit var repository: SettingsRepository

    companion object {
        const val CHANNEL_ID = "SyncChannel"
        const val NOTIFICATION_ID = 1
        var playlistsToSync: List<Playlist> = emptyList()
        var forceSync: Boolean = false
        const val ACTION_STOP = "STOP_SYNC"
    }

    override fun onCreate() {
        super.onCreate()
        syncManager = SyncManager(applicationContext)
        repository = SettingsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSync()
            return START_NOT_STICKY
        }

        val notification = createNotification("Starting sync...")
        startForeground(NOTIFICATION_ID, notification)

        syncJob = serviceScope.launch {
            SyncStatus.update(true, "Initializing...")
            try {
                val settings = repository.settingsFlow.first()
                var totalDownloaded = 0
                var totalRemoved = 0
                val initialRemovedFiles = mutableListOf<String>()

                val orphanPlaylistsRemoved = syncManager.cleanupOrphanPlaylists(playlistsToSync, settings, initialRemovedFiles)
                totalRemoved += orphanPlaylistsRemoved

                if (initialRemovedFiles.isNotEmpty()) {
                    SyncStatus.update(true, "Removing old playlists...", 0f, removedList = initialRemovedFiles)
                }

                for ((index, playlist) in playlistsToSync.withIndex()) {
                    if (!isActive) break
                    
                    val playlistBaseProgress = index.toFloat() / playlistsToSync.size
                    val playlistWeight = 1f / playlistsToSync.size
                    
                    val result = syncManager.syncPlaylist(
                        playlist = playlist,
                        settings = settings,
                        force = forceSync,
                        onProgress = { msg, songProgress ->
                            val totalProgress = playlistBaseProgress + (songProgress * playlistWeight)
                            SyncStatus.update(true, msg, totalProgress)
                            updateNotification(msg, (totalProgress * 100).toInt(), 100)
                        }
                    )
                    totalDownloaded += result.downloadedCount
                    totalRemoved += result.removedCount

                    SyncStatus.update(
                        syncing = true,
                        prog = playlistBaseProgress + playlistWeight,
                        errorList = result.failedSongs,
                        downloadedList = result.downloadedFiles,
                        removedList = result.removedFiles
                    )
                }
                
                val summaryMsg = when {
                    totalDownloaded > 0 && totalRemoved > 0 -> "Sync completed! ($totalDownloaded downloaded, $totalRemoved removed)"
                    totalDownloaded > 0 -> "Sync completed! ($totalDownloaded files downloaded)"
                    totalRemoved > 0 -> "Sync completed! ($totalRemoved files removed)"
                    else -> "Sync completed! Everything is up to date."
                }

                SyncStatus.update(false, if (isActive) summaryMsg else "Sync canceled.", 1f)
            } catch (e: Exception) {
                SyncStatus.update(false, "Error: ${e.localizedMessage}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopSync() {
        syncJob?.cancel()
        SyncStatus.update(false, "Sync stopped.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(content: String, progress: Int = 0, max: Int = 0): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SyncPL")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (max > 0) {
            builder.setProgress(max, progress, false)
        } else if (SyncStatus.isSyncing.value) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(content: String, progress: Int = 0, max: Int = 0) {
        val notification = createNotification(content, progress, max)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playlist Synchronization",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
