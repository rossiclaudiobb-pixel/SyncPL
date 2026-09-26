package com.example.myplaylistsync.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateLocalPath(it.toString()) 
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Mac Server Connection", style = MaterialTheme.typography.titleMedium)

            Text(
                text = "Leave empty; enter the Mac IP address only if connection fails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = settings.remoteHost,
                onValueChange = { viewModel.updateHost(it) },
                label = { Text("Mac Hostname or IP") },
                placeholder = { Text("syncpl.local") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = if (settings.httpPort.isEmpty()) "5001" else settings.httpPort,
                onValueChange = { },
                enabled = false,
                readOnly = true,
                label = { Text("HTTP Port (Default: 5001)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            Text("Phone Local Storage", style = MaterialTheme.typography.titleMedium)

            Button(onClick = { folderLauncher.launch(null) }) {
                Text(if (settings.localPathUri.isEmpty()) "Select Music Folder" else "Change Music Folder")
            }

            if (settings.localPathUri.isNotEmpty()) {
                Text("Selected path: ${settings.localPathUri}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Text("About & Legal", style = MaterialTheme.typography.titleMedium)

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://syncpl.org"))
                    context.startActivity(intent)
                }
            ) {
                Text("Official Website (syncpl.org)")
            }

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://syncpl.org/#terms"))
                    context.startActivity(intent)
                }
            ) {
                Text("Terms & Conditions")
            }

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://syncpl.org/#privacy"))
                    context.startActivity(intent)
                }
            ) {
                Text("Privacy Policy")
            }
        }
    }
}
