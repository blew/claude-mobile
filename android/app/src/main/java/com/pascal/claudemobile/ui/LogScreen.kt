package com.pascal.claudemobile.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pascal.claudemobile.data.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by Logger.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(Logger.snapshot()))
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy all")
                    }
                    IconButton(onClick = {
                        Logger.clear()
                        Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No log entries yet.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                items(entries) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
