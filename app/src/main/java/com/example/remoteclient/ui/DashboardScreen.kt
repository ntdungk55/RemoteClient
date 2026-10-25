package com.example.remoteclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    session: HandshakeResponse,
    endpoint: String,
    onSessionClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val isClosing by viewModel.isClosing.collectAsState()
    val closeError by viewModel.closeError.collectAsState()
    val closedSessionId by viewModel.closedSessionId.collectAsState()

    LaunchedEffect(closedSessionId, session.sessionId) {
        if (closedSessionId == session.sessionId) onSessionClosed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .then(modifier),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Connected", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Device: ${session.deviceName}")
        Text("Session: ${session.sessionId}")
        Text("Connected at: ${session.connectedAt}")
        Text("Bound target: ${session.target.windowTitle.ifBlank { "(Untitled window)" }}")
        Text("Process: ${session.target.processName} · PID ${session.target.pid}")
        Text("HWND: ${session.target.hwnd} · Created: ${session.target.creationTime}")
        Spacer(Modifier.height(20.dp))
        Text("Granted permissions", style = MaterialTheme.typography.titleMedium)
        PermissionRow("Connect", session.permissions.connect)
        PermissionRow("View stream", session.permissions.viewStream)
        PermissionRow("View game state", session.permissions.viewGameState)
        PermissionRow("Send chat", session.permissions.sendChat)
        PermissionRow("Discover tools", session.permissions.discoverTools)
        PermissionRow("Call read-only tools", session.permissions.callReadonlyTools)
        PermissionRow("Call mutating tools", session.permissions.callMutatingTools)
        closeError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.closeSession(endpoint, session.sessionId) },
            enabled = !isClosing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isClosing) {
                CircularProgressIndicator()
            } else {
                Text("Close session")
            }
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
