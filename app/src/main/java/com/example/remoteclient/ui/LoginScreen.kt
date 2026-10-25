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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel, modifier: Modifier = Modifier) {
    val endpointState by viewModel.endpoint.collectAsState()
    val status by viewModel.serverStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusError by viewModel.statusError.collectAsState()
    val loginState by viewModel.loginState.collectAsState()

    var endpoint by remember(endpointState) { mutableStateOf(endpointState) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Android") }
    var selectedTarget by remember { mutableStateOf<RemoteTarget?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .then(modifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Connect to Remote Server", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (endpoint.trimStart().startsWith("http://", ignoreCase = true)) {
            Text(
                "HTTP traffic is unencrypted. Use only on a trusted network.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available targets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { viewModel.refreshTargets(endpoint) },
                enabled = !isRefreshing
            ) {
                Text("Refresh")
            }
        }
        status?.let { serverStatus ->
            Text("Server: ${serverStatus.status} · Active sessions: ${serverStatus.sessions}")
            if (serverStatus.targets.isEmpty()) {
                Text("No available targets.")
            } else {
                serverStatus.targets.forEach { target ->
                    TargetOption(
                        target = target,
                        selected = selectedTarget?.hwnd == target.hwnd,
                        onSelect = { selectedTarget = target }
                    )
                }
            }
        }
        if (isRefreshing) {
            CircularProgressIndicator()
        }
        statusError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        (loginState as? LoginViewModel.LoginState.Failure)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                selectedTarget?.let { target ->
                    viewModel.login(endpoint, username, password, deviceName, target)
                }
            },
            enabled = loginState !is LoginViewModel.LoginState.Loading &&
                selectedTarget != null && status?.targets?.contains(selectedTarget) == true
        ) {
            if (loginState is LoginViewModel.LoginState.Loading) {
                CircularProgressIndicator()
            } else {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun TargetOption(
    target: RemoteTarget,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(target.windowTitle.ifBlank { "(Untitled window)" })
            Text(
                "${target.processName} · PID ${target.pid} · HWND ${target.hwnd}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
