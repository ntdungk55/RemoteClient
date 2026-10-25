package com.example.remoteclient.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remoteclient.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            viewModel.login(username, password)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }

        val loginState = viewModel.loginState.collectAsState()
        when (loginState.value) {
            is LoginViewModel.LoginState.Success -> {
                Toast.makeText(LocalContext.current, "Login Successful", Toast.LENGTH_SHORT).show()
            }
            is LoginViewModel.LoginState.Error -> {
                Toast.makeText(LocalContext.current, "Login Failed", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }
}
