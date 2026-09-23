package com.example.myplaylistsync.ui

import android.content.Intent
import com.example.myplaylistsync.network.SyncService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val playlists = viewModel.playlists.value
    val selectedPlaylists = viewModel.selectedPlaylists
    val forceSync = viewModel.forceSync.value
    
    // Stati osservati dal Service o locali
    val isSyncing by viewModel.isSyncing.collectAsState()
    val globalSyncStatus by viewModel.globalSyncStatus.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncFailures by viewModel.syncFailures.collectAsState()
    val syncDownloadedLog by viewModel.syncDownloadedLog.collectAsState()
    val syncRemovedLog by viewModel.syncRemovedLog.collectAsState()
    val localStatus = viewModel.localStatus.value

    var showFailuresDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPlaylists()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SyncPL") },
                actions = {
                    IconButton(onClick = { viewModel.loadPlaylists() }, enabled = !isSyncing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings, enabled = !isSyncing) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startSync(context) },
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                text = { Text("Sync") },
                expanded = !isSyncing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val statusToShow = if (isSyncing || globalSyncStatus.isNotEmpty()) globalSyncStatus else localStatus
            
            if (statusToShow.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusToShow,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = forceSync,
                    onCheckedChange = { viewModel.setForceSync(it) }
                )
                Text("Force overwrite existing files", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            val hasDetails = !isSyncing && (syncDownloadedLog.isNotEmpty() || syncRemovedLog.isNotEmpty() || syncFailures.isNotEmpty())
            if (hasDetails) {
                OutlinedButton(
                    onClick = { showDetailsDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("View last sync details")
                }
            }

            if (syncFailures.isNotEmpty() && !isSyncing) {
                Button(
                    onClick = { showFailuresDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("View errors (${syncFailures.size})")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { viewModel.selectAll(true) }) { Text("Select all") }
                TextButton(onClick = { viewModel.selectAll(false) }) { Text("Deselect all") }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists) { playlist ->
                    val isSelected = selectedPlaylists[playlist.name] ?: false
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songs.size} tracks") },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.togglePlaylist(playlist.name) }
                            )
                        }
                    )
                }
            }
        }
        
        if (showFailuresDialog) {
            AlertDialog(
                onDismissRequest = { showFailuresDialog = false },
                title = { Text("Unsynced tracks") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(syncFailures) { failure ->
                            Text(
                                text = "• $failure",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFailuresDialog = false }) { Text("Close") }
                }
            )
        }

        if (showDetailsDialog) {
            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                title = { Text("Last sync details") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (syncDownloadedLog.isNotEmpty()) {
                            item {
                                Text("Downloaded files (${syncDownloadedLog.size}):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            items(syncDownloadedLog) { item ->
                                Text("• $item", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                        if (syncRemovedLog.isNotEmpty()) {
                            item {
                                Text("Removed files (${syncRemovedLog.size}):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            }
                            items(syncRemovedLog) { item ->
                                Text("• $item", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                        if (syncFailures.isNotEmpty()) {
                            item {
                                Text("Errors (${syncFailures.size}):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            }
                            items(syncFailures) { failure ->
                                Text("• $failure", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDetailsDialog = false }) { Text("Close") }
                }
            )
        }

        if (isSyncing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        val stopIntent = Intent(context, SyncService::class.java).apply {
                            action = SyncService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Syncing in progress") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = syncProgress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(globalSyncStatus)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("The app can run in the background.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    }
}
