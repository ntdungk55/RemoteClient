package com.example.remoteclient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteclient.model.RemoteSession
import com.example.remoteclient.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardViewModel : ViewModel() {

    private val _serverState = MutableStateFlow("Unknown")
    val serverState: StateFlow<String> = _serverState

    private val _sessions = MutableStateFlow<List<RemoteSession>>(emptyList())
    val sessions: StateFlow<List<RemoteSession>> = _sessions

    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8765")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    init {
        fetchServerState()
        fetchSessions()
    }

    private fun fetchServerState() {
        viewModelScope.launch {
            try {
                val response = apiService.getStatus().execute()
                if (response.isSuccessful) {
                    _serverState.value = response.body()?.state?.name ?: "Unknown"
                }
            } catch (e: Exception) {
                _serverState.value = "Error"
            }
        }
    }

    private fun fetchSessions() {
        viewModelScope.launch {
            try {
                val response = apiService.getStatus().execute()
                if (response.isSuccessful) {
                    _sessions.value = response.body()?.sessions ?: emptyList()
                }
            } catch (e: Exception) {
                _sessions.value = emptyList()
            }
        }
    }

    fun closeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                apiService.closeSession(sessionId).execute()
                fetchSessions() // Refresh sessions after closing
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
