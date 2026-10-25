package com.example.remoteclient.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remoteclient.model.RemoteSession
import com.example.remoteclient.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val serverState by viewModel.serverState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Server State: ${serverState}", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Active Sessions:", style = MaterialTheme.typography.h6)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sessions) { session ->
                SessionItem(session = session, onClose = { viewModel.closeSession(session.id) })
            }
        }
    }
}

@Composable
fun SessionItem(session: RemoteSession, onClose: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Session ID: ${session.id}")
                Text("Target: ${session.target.windowTitle}")
            }
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}
