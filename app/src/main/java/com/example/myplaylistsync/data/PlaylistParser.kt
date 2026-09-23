package com.example.myplaylistsync.data

object PlaylistParser {
    private const val PLAYLIST_TAG = "<|>"
    private const val SONG_NAME_TAG = "<!!>"
    private const val SONG_PATH_TAG = "<??>"

    fun parse(content: String): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        
        // Split by playlist tag but keep the tag to identify starts
        val playlistBlocks = content.split(PLAYLIST_TAG).filter { it.isNotBlank() }
        
        for (block in playlistBlocks) {
            val lines = block.lines()
            val playlistName = lines.firstOrNull()?.trim() ?: continue
            val songs = mutableListOf<Song>()
            
            // Process the rest of the block for songs
            val remainingContent = lines.drop(1).joinToString("\n")
            val songBlocks = remainingContent.split(SONG_NAME_TAG).filter { it.isNotBlank() }
            
            for (songBlock in songBlocks) {
                val songParts = songBlock.split(SONG_PATH_TAG)
                if (songParts.size >= 2) {
                    val name = songParts[0].trim()
                    val path = songParts[1].trim().lines().firstOrNull()?.trim() ?: ""
                    if (name.isNotEmpty() && path.isNotEmpty()) {
                        songs.add(Song(name, path))
                    }
                }
            }
            
            playlists.add(Playlist(playlistName, songs))
        }

        return playlists
    }
}
