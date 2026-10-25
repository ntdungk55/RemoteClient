package com.example.remoteclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.viewmodel.LoginViewModel

@Composable
fun TargetSelectionScreen(
    state: LoginViewModel.LoginState.AwaitingTarget,
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val endpoint by viewModel.endpoint.collectAsState()
    var selectedTarget by remember(state.session.sessionId) {
        mutableStateOf<RemoteTarget?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .then(modifier),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Choose a game window", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Handshake successful. Select the game window for this session.")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.refreshTargets(endpoint) },
            enabled = !state.isLoadingTargets &&
                !state.isBindingTarget && !state.isCancellingSession
        ) {
            Text("Refresh windows")
        }
        if (state.isLoadingTargets) {
            CircularProgressIndicator()
        }
        if (!state.isLoadingTargets && state.targets.isEmpty()) {
            Text("No available game windows on the server.")
        }
        state.targets.forEach { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedTarget?.hwnd == target.hwnd,
                    onClick = { selectedTarget = target },
                    enabled = !state.isBindingTarget && !state.isCancellingSession
                )
                Column {
                    Text(target.windowTitle.ifBlank { "(Untitled window)" })
                    Text(
                        "${target.processName} · PID ${target.pid} · HWND ${target.hwnd}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { selectedTarget?.let { viewModel.selectTarget(endpoint, it) } },
            enabled = !state.isBindingTarget && !state.isCancellingSession &&
                selectedTarget != null && state.targets.contains(selectedTarget)
        ) {
            if (state.isBindingTarget) {
                CircularProgressIndicator()
            } else {
                Text("Select window")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.cancelTargetSelection(endpoint) },
            enabled = !state.isBindingTarget && !state.isCancellingSession
        ) {
            if (state.isCancellingSession) {
                CircularProgressIndicator()
            } else {
                Text("Cancel")
            }
        }
    }
}
