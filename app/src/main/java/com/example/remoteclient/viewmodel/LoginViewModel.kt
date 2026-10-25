package com.example.remoteclient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteclient.model.RemoteCredentials
import com.example.remoteclient.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginViewModel : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8765")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val response = apiService.handshake(RemoteCredentials(username, password)).execute()
                if (response.isSuccessful) {
                    _loginState.value = LoginState.Success
                } else {
                    _loginState.value = LoginState.Error
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error
            }
        }
    }

    sealed class LoginState {
        object Idle : LoginState()
        object Success : LoginState()
        object Error : LoginState()
    }
}
