package com.example.remoteclient.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteclient.model.ServerError
import com.example.remoteclient.network.ApiConfig
import com.example.remoteclient.network.SecureSessionStore
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SecureSessionStore(application)

    private val _isClosing = MutableStateFlow(false)
    val isClosing: StateFlow<Boolean> = _isClosing

    private val _closeError = MutableStateFlow<String?>(null)
    val closeError: StateFlow<String?> = _closeError

    private val _closedSessionId = MutableStateFlow<String?>(null)
    val closedSessionId: StateFlow<String?> = _closedSessionId

    fun closeSession(baseUrl: String, sessionId: String) {
        if (_isClosing.value) return
        viewModelScope.launch {
            _isClosing.value = true
            _closeError.value = null
            _closedSessionId.value = null
            try {
                val response = ApiConfig.createApiService(baseUrl)
                    .closeSession(sessionId, ApiConfig.CLOSE_SESSION_AUTHORIZATION)
                if (!response.isSuccessful) {
                    throw IOException(responseError(response))
                }
                sessionStore.clear()
                _closedSessionId.value = sessionId
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _closeError.value = exceptionMessage(exception)
            } finally {
                _isClosing.value = false
            }
        }
    }

    private fun responseError(response: retrofit2.Response<*>): String {
        val serverError = response.errorBody()?.string()?.let { body ->
            try {
                Gson().fromJson(body, ServerError::class.java)
            } catch (_: JsonParseException) {
                null
            }
        }
        val code = serverError?.code ?: serverError?.error
        val detail = serverError?.message ?: serverError?.detail
        val description = when {
            response.code() == 401 -> "Session authorization was rejected"
            response.code() == 403 || code == "permission_denied" -> "Permission denied"
            response.code() == 404 -> "The server or session was not found"
            response.code() >= 500 -> "The server encountered an error"
            else -> "Could not close the session"
        }
        return listOfNotNull(description, "HTTP ${response.code()}", code, detail)
            .joinToString(": ")
    }

    private fun exceptionMessage(exception: Exception): String = when (exception) {
        is SocketTimeoutException -> "The server timed out. Try closing the session again."
        is UnknownHostException -> "Server host could not be reached. Check the URL and network."
        is IOException -> exception.message ?: "Network request failed."
        is IllegalArgumentException -> exception.message ?: "Check the server URL."
        else -> "Could not close the session: ${exception.message ?: exception.javaClass.simpleName}"
    }
}
