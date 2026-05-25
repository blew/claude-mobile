package com.pascal.claudemobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pascal.claudemobile.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    var serverUrl by remember { mutableStateOf(viewModel.serverUrl) }
    var apiKey by remember { mutableStateOf(viewModel.apiKey) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; saved = false },
                label = { Text("Server URL") },
                placeholder = { Text("https://…devtunnels.ms") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Button(
                onClick = {
                    viewModel.saveSettings(serverUrl.trim(), apiKey.trim())
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            if (saved) {
                Text(
                    "Saved. Start a new chat for changes to take effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = onOpenLog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View debug log")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "1. Run  python claude_proxy.py  on your laptop\n" +
                "2. In VS Code → Ports panel → Forward 8765 → set Public\n" +
                "3. Paste the devtunnel URL above\n" +
                "4. Paste the API key printed in the proxy terminal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
