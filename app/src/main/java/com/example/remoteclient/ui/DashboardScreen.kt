package com.example.remoteclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    session: HandshakeResponse,
    endpoint: String,
    onSessionClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val activeSession by viewModel.activeSession.collectAsState()
    val currentSession = activeSession ?: session
    val isClosing by viewModel.isClosing.collectAsState()
    val closedSessionId by viewModel.closedSessionId.collectAsState()
    var mode by remember(session.sessionId) { mutableStateOf(DashboardMode.ChatOnly) }
    var showCloseConfirmation by remember(session.sessionId) { mutableStateOf(false) }
    var showTargetPicker by remember(session.sessionId) { mutableStateOf(false) }
    var requestedTargetHwnd by remember(session.sessionId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(session.sessionId) {
        viewModel.setActiveSession(session)
    }

    LaunchedEffect(closedSessionId, session.sessionId) {
        if (closedSessionId == session.sessionId) onSessionClosed()
    }
    LaunchedEffect(currentSession.target?.hwnd) {
        if (requestedTargetHwnd == currentSession.target?.hwnd) {
            requestedTargetHwnd = null
            showTargetPicker = false
        }
    }
    LaunchedEffect(session.sessionId, currentSession.target?.hwnd, mode) {
        viewModel.stopGameStream()
        if (mode != DashboardMode.ChatOnly) {
            viewModel.startGameStream(endpoint, session.sessionId)
        }
        try {
            while (true) {
                if (mode != DashboardMode.ViewOnly) {
                    viewModel.refreshMessages(endpoint, session.sessionId)
                }
                delay(1500)
            }
        } finally {
            viewModel.stopGameStream()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                currentSession.target?.windowTitle?.ifBlank { "Connected" } ?: "Connected",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    showTargetPicker = true
                    viewModel.loadTargets(endpoint)
                },
                enabled = !isClosing
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Change game window")
            }
            IconButton(
                onClick = { showCloseConfirmation = true },
                enabled = !isClosing
            ) {
                if (isClosing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.Close, contentDescription = "Close session")
                }
            }
        }
        when (mode) {
            DashboardMode.ViewOnly -> GameViewPanel(viewModel, Modifier.weight(1f))
            DashboardMode.ChatOnly -> RemoteChatPanel(
                endpoint, session.sessionId, currentSession.permissions.sendChat, viewModel,
                Modifier.weight(1f)
            )
            DashboardMode.Split -> {
                GameViewPanel(viewModel, Modifier.weight(1f))
                RemoteChatPanel(
                    endpoint, session.sessionId, currentSession.permissions.sendChat, viewModel,
                    Modifier.weight(1f)
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeButton(
                    icon = Icons.Filled.Visibility,
                    description = "View only",
                    selected = mode == DashboardMode.ViewOnly,
                    enabled = currentSession.permissions.viewStream
                ) { mode = DashboardMode.ViewOnly }
                ModeButton(
                    icon = Icons.Filled.Chat,
                    description = "Chat only",
                    selected = mode == DashboardMode.ChatOnly
                ) { mode = DashboardMode.ChatOnly }
                ModeButton(
                    icon = Icons.Filled.Splitscreen,
                    description = "View and chat",
                    selected = mode == DashboardMode.Split,
                    enabled = currentSession.permissions.viewStream
                ) { mode = DashboardMode.Split }
            }
        }
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isClosing) showCloseConfirmation = false },
            title = { Text("Close session?") },
            text = { Text("This will disconnect this device from the server. You will need to connect again to resume.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmation = false
                        viewModel.closeSession(endpoint, session.sessionId)
                    },
                    enabled = !isClosing
                ) {
                    Text("Close session")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloseConfirmation = false },
                    enabled = !isClosing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTargetPicker) {
        TargetPickerDialog(
            currentTarget = currentSession.target,
            targets = viewModel.availableTargets.collectAsState().value,
            isLoading = viewModel.isLoadingTargets.collectAsState().value,
            isChanging = viewModel.isChangingTarget.collectAsState().value,
            error = viewModel.targetError.collectAsState().value,
            onDismiss = { showTargetPicker = false },
            onRefresh = { viewModel.loadTargets(endpoint) },
            onSelect = { target ->
                requestedTargetHwnd = target.hwnd
                viewModel.changeTarget(endpoint, target)
            }
        )
    }
}

private enum class DashboardMode { ViewOnly, ChatOnly, Split }

@Composable
private fun ModeButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RemoteChatPanel(
    endpoint: String,
    sessionId: String,
    canSend: Boolean,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val chatError by viewModel.chatError.collectAsState()
    val isSending by viewModel.isSendingMessage.collectAsState()
    val hasMoreMessages by viewModel.hasMoreMessages.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlderMessages.collectAsState()
    var draft by remember(sessionId) { mutableStateOf("") }
    var initialScrollComplete by remember(sessionId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !initialScrollComplete) {
            listState.scrollToItem(messages.lastIndex)
            initialScrollComplete = true
        } else if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.lastIndex - 1) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(listState, hasMoreMessages) {
        snapshotFlow {
            initialScrollComplete &&
                listState.isScrollInProgress &&
                listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .filter { it && hasMoreMessages }
            .collect {
                viewModel.loadOlderMessages(endpoint, sessionId)
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (hasMoreMessages || isLoadingOlder) {
                item(key = "load-older") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoadingOlder) {
                            CircularProgressIndicator()
                        } else {
                            TextButton(
                                onClick = { viewModel.loadOlderMessages(endpoint, sessionId) }
                            ) {
                                Text("Load earlier", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val fromClient = message.side == "right" || message.side == "automatic"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (fromClient) Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(
                                color = if (fromClient) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }

            }
        }
        chatError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", fontSize = 13.sp) },
                enabled = canSend && !isSending,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            viewModel.sendMessage(endpoint, sessionId, draft)
                            draft = ""
                        }
                    }
                )
            )
            Button(
                onClick = {
                    viewModel.sendMessage(endpoint, sessionId, draft)
                    draft = ""
                },
                enabled = canSend && !isSending && draft.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }
}

@Composable
private fun TargetPickerDialog(
    currentTarget: RemoteTarget?,
    targets: List<RemoteTarget>,
    isLoading: Boolean,
    isChanging: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (RemoteTarget) -> Unit
) {
    var selectedTarget by remember(currentTarget?.hwnd) { mutableStateOf(currentTarget) }
    AlertDialog(
        onDismissRequest = { if (!isChanging) onDismiss() },
        title = { Text("Choose game window") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                Button(onClick = onRefresh, enabled = !isLoading && !isChanging) {
                    Text("Refresh windows")
                }
                if (isLoading) {
                    CircularProgressIndicator()
                }
                if (!isLoading && targets.isEmpty()) {
                    Text("No available game windows.")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    targets.forEach { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTarget?.hwnd == target.hwnd,
                                onClick = { selectedTarget = target },
                                enabled = !isChanging
                            )
                            Column {
                                Text(target.windowTitle.ifBlank { "(Untitled window)" })
                                Text(
                                    "${target.processName} · PID ${target.pid}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTarget?.let(onSelect) },
                enabled = !isChanging &&
                    selectedTarget != null &&
                    targets.contains(selectedTarget) &&
                    selectedTarget != currentTarget
            ) {
                if (isChanging) CircularProgressIndicator() else Text("Change window")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isChanging) { Text("Cancel") }
        }
    )
}

@Composable
private fun GameViewPanel(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val frame by viewModel.gameFrame.collectAsState()
    val frameError by viewModel.frameError.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (frame != null) {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Live game window",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator()
        }
        frameError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            if (granted) "Granted" else "Denied",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
