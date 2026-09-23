package com.example.myplaylistsync.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myplaylistsync.data.Playlist
import com.example.myplaylistsync.data.Settings
import com.example.myplaylistsync.data.SettingsRepository
import com.example.myplaylistsync.data.SyncManager
import com.example.myplaylistsync.network.SyncService
import com.example.myplaylistsync.network.SyncStatus
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val syncManager: SyncManager,
    private val repository: SettingsRepository
) : ViewModel() {

    // Stato locale per il caricamento delle playlist
    var localStatus = mutableStateOf("")
        private set

    // Osserva lo stato globale della sincronizzazione dal Service
    val isSyncing = SyncStatus.isSyncing
    val globalSyncStatus = SyncStatus.message
    val syncProgress = SyncStatus.progress
    val syncFailures = SyncStatus.failures
    val syncDownloadedLog = SyncStatus.downloadedLog
    val syncRemovedLog = SyncStatus.removedLog

    val settings: StateFlow<Settings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Settings()
        )

    var playlists = mutableStateOf<List<Playlist>>(emptyList())
        private set

    var selectedPlaylists = mutableStateMapOf<String, Boolean>()
        private set

    var forceSync = mutableStateOf(false)
        private set

    fun loadPlaylists() {
        viewModelScope.launch {
            localStatus.value = "Loading settings..."
            val currentSettings = repository.settingsFlow.first()
            val effectiveSettings = if (currentSettings.remoteHost.isBlank()) {
                currentSettings.copy(remoteHost = "syncpl.local")
            } else {
                currentSettings
            }
            
            localStatus.value = "Loading remote playlists..."
            try {
                val remotePlaylists = syncManager.loadRemotePlaylists(effectiveSettings)
                playlists.value = remotePlaylists
                
                val localPlaylists = syncManager.getLocalPlaylistNames(currentSettings.localPathUri)
                
                selectedPlaylists.clear()
                remotePlaylists.forEach { playlist ->
                    val safeName = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    selectedPlaylists[playlist.name] = localPlaylists.contains(safeName)
                }
                
                localStatus.value = if (remotePlaylists.isEmpty()) "No playlists found." else "Playlists loaded."
            } catch (e: Exception) {
                localStatus.value = e.message ?: "Error loading playlists."
            }
        }
    }

    fun togglePlaylist(playlistName: String) {
        val currentStatus = selectedPlaylists[playlistName] ?: false
        val newStatus = !currentStatus
        selectedPlaylists[playlistName] = newStatus
        
        if (!newStatus) {
            viewModelScope.launch {
                val currentSettings = repository.settingsFlow.first()
                val deleted = syncManager.deletePlaylist(playlistName, currentSettings.localPathUri)
                if (deleted) {
                    localStatus.value = "Playlist $playlistName removed."
                }
            }
        }
    }

    fun startSync(context: Context) {
        val toSync = playlists.value.filter { selectedPlaylists[it.name] == true }
        if (toSync.isEmpty()) {
            localStatus.value = "Select at least one playlist."
            return
        }

        // Delega la sincronizzazione al Service
        SyncService.playlistsToSync = toSync
        SyncService.forceSync = forceSync.value
        
        val intent = Intent(context, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    fun setForceSync(value: Boolean) {
        forceSync.value = value
    }

    fun selectAll(value: Boolean) {
        playlists.value.forEach { playlist ->
            selectedPlaylists[playlist.name] = value
            if (!value) {
                // Se deselezioniamo tutto, cancelliamo anche i file locali
                viewModelScope.launch {
                    val currentSettings = repository.settingsFlow.first()
                    syncManager.deletePlaylist(playlist.name, currentSettings.localPathUri)
                }
            }
        }
        if (!value) {
            localStatus.value = "All playlists removed from device."
        }
    }
}
