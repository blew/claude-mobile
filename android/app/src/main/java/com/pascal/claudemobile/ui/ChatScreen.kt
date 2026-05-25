package com.pascal.claudemobile.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pascal.claudemobile.ChatViewModel
import com.pascal.claudemobile.R
import com.pascal.claudemobile.data.Message
import com.pascal.claudemobile.data.Role
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    error?.let { err ->
        LaunchedEffect(err) {
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                // safeDrawing.only(Bottom) = max(IME, nav bar). When keyboard is up,
                // input sits above keyboard. When closed, it sits above nav buttons.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Claude…") },
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        }),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AI Workspaces",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == Role.USER
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val copy: () -> Unit = {
        if (message.content.isNotBlank()) {
            clipboard.setText(AnnotatedString(message.content))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                    )
                    .combinedClickable(onClick = {}, onLongClick = copy)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = copy),
            ) {
                if (message.content.isEmpty() && message.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default,
                                lineHeight = 22.sp,
                            ),
                        )
                    }
                    if (message.isStreaming) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.3f))
                    }
                }
            }
        }
    }
}
