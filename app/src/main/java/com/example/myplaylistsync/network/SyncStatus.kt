package com.example.myplaylistsync.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SyncStatus {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _failures = MutableStateFlow<List<String>>(emptyList())
    val failures = _failures.asStateFlow()

    private val _downloadedLog = MutableStateFlow<List<String>>(emptyList())
    val downloadedLog = _downloadedLog.asStateFlow()

    private val _removedLog = MutableStateFlow<List<String>>(emptyList())
    val removedLog = _removedLog.asStateFlow()

    fun update(
        syncing: Boolean,
        msg: String = "",
        prog: Float = 0f,
        errorList: List<String> = emptyList(),
        downloadedList: List<String> = emptyList(),
        removedList: List<String> = emptyList()
    ) {
        _isSyncing.value = syncing
        _message.value = msg
        _progress.value = prog
        if (syncing && prog == 0f) {
            _failures.value = emptyList()
            _downloadedLog.value = emptyList()
            _removedLog.value = emptyList()
        }
        if (errorList.isNotEmpty()) {
            _failures.value = _failures.value + errorList
        }
        if (downloadedList.isNotEmpty()) {
            _downloadedLog.value = _downloadedLog.value + downloadedList
        }
        if (removedList.isNotEmpty()) {
            _removedLog.value = _removedLog.value + removedList
        }
    }
}
