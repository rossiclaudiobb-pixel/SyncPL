package com.example.myplaylistsync.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer

data class SyncResult(
    val downloadedCount: Int,
    val removedCount: Int = 0,
    val downloadedFiles: List<String> = emptyList(),
    val removedFiles: List<String> = emptyList(),
    val failedSongs: List<String> = emptyList()
)

class SyncManager(
    private val context: Context
) {
    private val httpClient = OkHttpClient()

    private fun getCandidateHosts(settings: Settings): List<String> {
        val userHost = settings.remoteHost.trim()
        val hosts = mutableListOf<String>()
        if (userHost.isNotBlank()) {
            hosts.add(userHost)
        }
        if (!hosts.contains("syncpl.local")) {
            hosts.add("syncpl.local")
        }
        if (!hosts.contains("10.0.2.2")) {
            hosts.add("10.0.2.2")
        }
        return hosts
    }

    suspend fun loadRemotePlaylists(settings: Settings): List<Playlist> = withContext(Dispatchers.IO) {
        val candidateHosts = getCandidateHosts(settings)
        var lastException: Exception? = null

        for (host in candidateHosts) {
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(settings.httpPort.toIntOrNull() ?: 5001)
                .addPathSegment("playlist")
                .build()

            try {
                return@withContext httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    PlaylistParser.parse(response.body?.string() ?: "")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw Exception("Python Server Error: ${lastException?.localizedMessage ?: "Connection failed"}")
    }

    fun getLocalPlaylistNames(localPathUri: String): List<String> {
        if (localPathUri.isEmpty()) return emptyList()
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(localPathUri))
        return rootDoc?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }
            ?.map { Normalizer.normalize(it, Normalizer.Form.NFC) } ?: emptyList()
    }

    private fun getSafeName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Normalizer.normalize(sanitized, Normalizer.Form.NFC)
    }

    private fun getSafeSongFileName(remotePath: String): String {
        val rawName = remotePath.replace("\\", "/").substringAfterLast("/")
        val sanitized = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Normalizer.normalize(sanitized, Normalizer.Form.NFC)
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/*"
        } else {
            "audio/*"
        }
    }

    private fun normalizeSafFileName(fileName: String): String {
        val nfcName = Normalizer.normalize(fileName, Normalizer.Form.NFC)
        val p1 = nfcName.replace(Regex("\\s*\\(\\d+\\)$"), "")
        val nameWithoutExt = p1.substringBeforeLast('.', p1)
        val ext = p1.substringAfterLast('.', "")
        val cleanBase = nameWithoutExt.replace(Regex("\\s*\\(\\d+\\)$"), "")
        return if (ext.isNotEmpty() && ext != p1) "$cleanBase.$ext" else cleanBase
    }

    private fun computeLocalSongFileNames(playlist: Playlist): List<String> {
        val seenCounts = mutableMapOf<String, Int>()
        return playlist.songs.map { song ->
            val baseName = getSafeSongFileName(song.remotePath)
            if (baseName.isEmpty()) return@map ""

            val count = (seenCounts[baseName] ?: 0) + 1
            seenCounts[baseName] = count

            if (count == 1) {
                baseName
            } else {
                val nameWithoutExt = baseName.substringBeforeLast('.', baseName)
                val ext = baseName.substringAfterLast('.', "")
                if (ext.isNotEmpty() && ext != baseName) {
                    "$nameWithoutExt ($count).$ext"
                } else {
                    "$baseName ($count)"
                }
            }
        }
    }

    private fun cleanOrphanTracks(
        playlistDoc: DocumentFile,
        playlist: Playlist,
        safeName: String,
        removedFiles: MutableList<String>
    ): Int {
        val localSongNames = computeLocalSongFileNames(playlist)
        val expectedFileNames = localSongNames.filter { it.isNotEmpty() }.flatMap { name ->
            listOf(name, normalizeSafFileName(name))
        }.toSet()
        val m3uName = "$safeName.m3u"

        var removedCount = 0
        playlistDoc.listFiles().forEach { file ->
            val rawName = file.name
            if (rawName != null && !rawName.startsWith(".")) {
                val fileName = Normalizer.normalize(rawName, Normalizer.Form.NFC)
                val cleanFileName = normalizeSafFileName(fileName)
                if (fileName != m3uName && !expectedFileNames.contains(fileName) && !expectedFileNames.contains(cleanFileName)) {
                    if (file.delete()) {
                        removedCount++
                        removedFiles.add("${playlist.name} → $fileName")
                    }
                }
            }
        }
        return removedCount
    }

    fun cleanupOrphanPlaylists(
        playlistsToSync: List<Playlist>,
        settings: Settings,
        removedFiles: MutableList<String> = mutableListOf()
    ): Int {
        if (settings.localPathUri.isEmpty()) return 0
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(settings.localPathUri)) ?: return 0
        val validSafeNames = playlistsToSync.map { getSafeName(it.name) }.toSet()

        var removedCount = 0
        rootDoc.listFiles().forEach { file ->
            if (file.isDirectory) {
                val rawName = file.name
                if (rawName != null) {
                    val folderName = Normalizer.normalize(rawName, Normalizer.Form.NFC)
                    if (!validSafeNames.contains(folderName)) {
                        if (file.delete()) {
                            removedCount++
                            removedFiles.add("Playlist folder: $folderName")
                        }
                    }
                }
            }
        }
        return removedCount
    }

    suspend fun syncPlaylist(
        playlist: Playlist,
        settings: Settings,
        force: Boolean,
        onProgress: (String, Float) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        var count = 0
        val failed = mutableListOf<String>()
        val downloadedFiles = mutableListOf<String>()
        val removedFiles = mutableListOf<String>()

        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(settings.localPathUri))
            ?: return@withContext SyncResult(0, 0, failedSongs = listOf("Local storage error"))
        val safeName = getSafeName(playlist.name)
        val playlistDoc = rootDoc.findFile(safeName) ?: rootDoc.createDirectory(safeName)
            ?: return@withContext SyncResult(0, 0, failedSongs = listOf("Playlist folder error"))

        val localSongNames = computeLocalSongFileNames(playlist)
        val removedCount = cleanOrphanTracks(playlistDoc, playlist, safeName, removedFiles)
        val localFiles = playlistDoc.listFiles().mapNotNull { it.name }
            .flatMap { name ->
                val nfc = Normalizer.normalize(name, Normalizer.Form.NFC)
                listOf(nfc, normalizeSafFileName(nfc))
            }.toSet()

        playlist.songs.forEachIndexed { index, song ->
            val progress = (index + 1).toFloat() / playlist.songs.size
            val fileName = localSongNames[index]
            if (fileName.isEmpty() || (localFiles.contains(fileName) && !force)) return@forEachIndexed
            onProgress("${playlist.name}: [$index/${playlist.songs.size}]", progress)

            val cleanRemotePath = song.remotePath.replace("\\", "/")
            val pathVariants = listOf(cleanRemotePath, Normalizer.normalize(cleanRemotePath, Normalizer.Form.NFD)).distinct()

            var success = false
            var lastError = "Not found on server"
            val mimeType = getMimeType(fileName)

            val candidateHosts = getCandidateHosts(settings)
            for (host in candidateHosts) {
                for (macPath in pathVariants) {
                    val url = HttpUrl.Builder()
                        .scheme("http")
                        .host(host)
                        .port(settings.httpPort.toIntOrNull() ?: 5001)
                        .addPathSegment("file")
                        .addQueryParameter("path", macPath)
                        .build()

                    try {
                        httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val tf = if (localFiles.contains(fileName)) playlistDoc.findFile(fileName) else playlistDoc.createFile(mimeType, fileName)
                                tf?.let {
                                    context.contentResolver.openOutputStream(it.uri)?.use { out -> resp.body?.byteStream()?.use { it.copyTo(out) } }
                                    count++
                                    downloadedFiles.add("${playlist.name} → $fileName")
                                    success = true
                                }
                            } else {
                                lastError = "HTTP Error ${resp.code}: ${resp.message}"
                            }
                        }
                    } catch (e: Exception) {
                        lastError = "Connection error: ${e.localizedMessage}"
                    }
                    if (success) break
                }
                if (success) break
            }
            if (!success) {
                val debugUrl = "http://${settings.remoteHost}:${settings.httpPort}/file?path=${cleanRemotePath}"
                failed.add("${song.name} ($lastError) - URL: $debugUrl")
            }
        }
        generateM3U(playlist, playlistDoc, safeName)
        return@withContext SyncResult(
            downloadedCount = count,
            removedCount = removedCount,
            downloadedFiles = downloadedFiles,
            removedFiles = removedFiles,
            failedSongs = failed
        )
    }

    private fun generateM3U(playlist: Playlist, playlistDoc: DocumentFile, safeName: String) {
        val localSongNames = computeLocalSongFileNames(playlist)
        val diskFiles = playlistDoc.listFiles().mapNotNull { it.name }
        val m3uName = "$safeName.m3u"
        playlistDoc.findFile(m3uName)?.delete()
        playlistDoc.createFile("audio/x-mpegurl", m3uName)?.let { mf ->
            context.contentResolver.openOutputStream(mf.uri)?.use { out ->
                out.bufferedWriter().use { w ->
                    playlist.songs.forEachIndexed { index, _ ->
                        val fn = localSongNames.getOrNull(index) ?: ""
                        if (fn.isNotEmpty()) {
                            val match = diskFiles.find { diskFile ->
                                val nfc = Normalizer.normalize(diskFile, Normalizer.Form.NFC)
                                nfc == fn || normalizeSafFileName(nfc) == fn
                            }
                            if (match != null) {
                                w.write(match)
                                w.newLine()
                            }
                        }
                    }
                }
            }
        }
    }

    fun deletePlaylist(playlistName: String, localPathUri: String): Boolean {
        if (localPathUri.isEmpty()) return false
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(localPathUri))
            val safeName = getSafeName(playlistName)
            return rootDoc?.findFile(safeName)?.delete() ?: false
        } catch (e: Exception) { return false }
    }
}
