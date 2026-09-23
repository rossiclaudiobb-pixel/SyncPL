package com.example.myplaylistsync.data

data class Song(
    val name: String,
    val remotePath: String
)

data class Playlist(
    val name: String,
    val songs: List<Song> = emptyList()
)
