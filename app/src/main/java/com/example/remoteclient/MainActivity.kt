package com.example.remoteclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remoteclient.ui.DashboardScreen
import com.example.remoteclient.ui.LoginScreen
import com.example.remoteclient.ui.theme.RemoteClientTheme
import com.example.remoteclient.viewmodel.LoginViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteClientTheme {
                val loginViewModel: LoginViewModel = viewModel()
                val loginState by loginViewModel.loginState.collectAsState()
                val endpoint by loginViewModel.endpoint.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val state = loginState) {
                        is LoginViewModel.LoginState.Success -> DashboardScreen(
                            session = state.session,
                            endpoint = endpoint,
                            onSessionClosed = loginViewModel::sessionClosed,
                            modifier = Modifier.padding(innerPadding)
                        )
                        else -> LoginScreen(
                            viewModel = loginViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
