package com.example.remoteclient.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.model.ServerError
import com.example.remoteclient.model.ServerStatus
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
import retrofit2.Response

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val sessionStore = SecureSessionStore(application)

    private val _endpoint = MutableStateFlow(
        preferences.getString(ENDPOINT_KEY, ApiConfig.DEFAULT_BASE_URL) ?: ApiConfig.DEFAULT_BASE_URL
    )
    val endpoint: StateFlow<String> = _endpoint

    private val _serverStatus = MutableStateFlow<ServerStatus?>(null)
    val serverStatus: StateFlow<ServerStatus?> = _serverStatus

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    init {
        refreshTargets(_endpoint.value)
    }

    fun refreshTargets(rawEndpoint: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _statusError.value = null
            try {
                val baseUrl = saveEndpoint(rawEndpoint)
                val response = ApiConfig.createApiService(baseUrl).getStatus()
                if (response.isSuccessful) {
                    _serverStatus.value = response.body()
                        ?: throw IOException("The server returned an empty status response.")
                } else {
                    _statusError.value = responseError(response)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _statusError.value = exceptionMessage(exception)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun login(
        rawEndpoint: String,
        username: String,
        password: String,
        deviceName: String,
        selectedTarget: RemoteTarget
    ) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                require(username.isNotBlank()) { "Enter the server username." }
                require(password.isNotBlank()) { "Enter the server password." }
                require(deviceName.isNotBlank()) { "Enter a device name." }
                require(selectedTarget.hwnd > 0) { "Select a valid target window." }

                val baseUrl = saveEndpoint(rawEndpoint)
                val api = ApiConfig.createApiService(baseUrl)
                val statusResponse = api.getStatus()
                if (!statusResponse.isSuccessful) {
                    throw ApiRequestException(responseError(statusResponse))
                }
                val status = statusResponse.body()
                    ?: throw IOException("The server returned an empty status response.")
                if (status.targets.none { it == selectedTarget }) {
                    throw ApiRequestException(
                        "The selected target is no longer available. Refresh targets and select again."
                    )
                }

                val response = api.handshake(
                    HandshakeRequest(
                        username = username.trim(),
                        password = password,
                        deviceName = deviceName.trim(),
                        targetHwnd = selectedTarget.hwnd
                    )
                )
                if (!response.isSuccessful) {
                    throw ApiRequestException(responseError(response))
                }
                val session = response.body()
                    ?: throw IOException("The server returned an empty handshake response.")
                require(session.sessionId.isNotBlank()) { "The server returned an invalid session ID." }
                if (session.target != selectedTarget) {
                    val closeResponse = api.closeSession(
                        session.sessionId,
                        ApiConfig.CLOSE_SESSION_AUTHORIZATION
                    )
                    val cleanupMessage = if (closeResponse.isSuccessful) {
                        "The unexpected session was closed."
                    } else {
                        "Could not close the unexpected session: ${responseError(closeResponse)}"
                    }
                    throw ApiRequestException(
                        "The server bound the session to a different target. $cleanupMessage"
                    )
                }
                sessionStore.save(session.sessionId)
                _loginState.value = LoginState.Success(session)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _loginState.value = LoginState.Failure(exceptionMessage(exception))
            }
        }
    }

    fun sessionClosed() {
        _loginState.value = LoginState.Idle
    }

    private fun saveEndpoint(rawEndpoint: String): String {
        val normalized = ApiConfig.normalizeBaseUrl(rawEndpoint)
        if (_endpoint.value != normalized) {
            check(preferences.edit().putString(ENDPOINT_KEY, normalized).commit()) {
                "Could not save the server URL."
            }
            _serverStatus.value = null
            _endpoint.value = normalized
        }
        return normalized
    }

    private fun responseError(response: Response<*>): String {
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
            response.code() == 401 -> "Authentication failed"
            response.code() == 403 || code == "permission_denied" -> "Permission denied"
            response.code() == 409 || code == "target_unavailable" -> "The selected target is unavailable"
            response.code() == 404 -> "The server or session was not found"
            response.code() >= 500 -> "The server encountered an error"
            else -> "Request failed"
        }
        return listOfNotNull(description, "HTTP ${response.code()}", code, detail)
            .joinToString(": ")
    }

    private fun exceptionMessage(exception: Exception): String = when (exception) {
        is ApiRequestException -> exception.message ?: "Request failed."
        is SocketTimeoutException -> "The server timed out. Check the connection and try again."
        is UnknownHostException -> "Server host could not be reached. Check the URL and network."
        is IOException -> "Network request failed: ${exception.message ?: "connection error"}"
        is IllegalArgumentException -> exception.message ?: "Check the server URL and request values."
        else -> "Request failed: ${exception.message ?: exception.javaClass.simpleName}"
    }

    sealed class LoginState {
        data object Idle : LoginState()
        data object Loading : LoginState()
        data class Success(val session: HandshakeResponse) : LoginState()
        data class Failure(val message: String) : LoginState()
    }

    private class ApiRequestException(message: String) : IOException(message)

    private companion object {
        const val PREFERENCES_NAME = "remote-client"
        const val ENDPOINT_KEY = "server-endpoint"
    }
}
