package com.example.remoteclient.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.example.remoteclient.model.BindTargetRequest
import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.model.ServerError
import com.example.remoteclient.network.ApiConfig
import com.example.remoteclient.network.RememberedCredentialsStore
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
    private val credentialsStore = RememberedCredentialsStore(application)
    private val rememberedCredentials = credentialsStore.load()

    private val _endpoint = MutableStateFlow(
        loadSavedEndpoint()
    )
    val endpoint: StateFlow<String> = _endpoint

    private val _username = MutableStateFlow(rememberedCredentials?.username.orEmpty())
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow(rememberedCredentials?.password.orEmpty())
    val password: StateFlow<String> = _password

    private val _rememberCredentials = MutableStateFlow(
        preferences.getBoolean(REMEMBER_CREDENTIALS_KEY, false)
    )
    val rememberCredentials: StateFlow<Boolean> = _rememberCredentials

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun updateUsername(value: String) {
        _username.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun setRememberCredentials(remember: Boolean) {
        check(preferences.edit().putBoolean(REMEMBER_CREDENTIALS_KEY, remember).commit()) {
            "Could not save the remember-credentials setting."
        }
        _rememberCredentials.value = remember
        if (remember) {
            credentialsStore.save(_username.value.trim(), _password.value)
        } else {
            credentialsStore.clear()
        }
    }

    fun refreshTargets(rawEndpoint: String) {
        val awaitingTarget = _loginState.value as? LoginState.AwaitingTarget ?: return
        viewModelScope.launch {
            _loginState.value = awaitingTarget.copy(isLoadingTargets = true, error = null)
            try {
                val baseUrl = saveEndpoint(rawEndpoint)
                val response = ApiConfig.createApiService(baseUrl).getStatus()
                if (response.isSuccessful) {
                    val status = response.body()
                        ?: throw IOException("The server returned an empty status response.")
                    val currentState = _loginState.value as? LoginState.AwaitingTarget
                    if (currentState != null) {
                        _loginState.value = currentState.copy(
                            targets = status.targets,
                            isLoadingTargets = false,
                            error = null
                        )
                    }
                } else {
                    val error = responseError(response)
                    updateTargetSelectionError(error)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val error = exceptionMessage(exception)
                updateTargetSelectionError(error)
            } finally {
                val currentState = _loginState.value as? LoginState.AwaitingTarget
                if (currentState?.isLoadingTargets == true) {
                    _loginState.value = currentState.copy(isLoadingTargets = false)
                }
            }
        }
    }

    fun login(
        rawEndpoint: String,
        username: String,
        password: String,
        deviceName: String
    ) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            var pendingSession: HandshakeResponse? = null
            try {
                require(deviceName.isNotBlank()) { "Enter a device name." }

                val baseUrl = saveEndpoint(rawEndpoint)
                val api = ApiConfig.createApiService(baseUrl)
                val response = api.handshake(
                    HandshakeRequest(
                        username = username.trim(),
                        password = password,
                        deviceName = deviceName.trim()
                    )
                )
                if (!response.isSuccessful) {
                    throw ApiRequestException(responseError(response))
                }
                if (_rememberCredentials.value) {
                    credentialsStore.save(username.trim(), password)
                }
                val session = response.body()
                    ?: throw IOException("The server returned an empty handshake response.")
                require(session.sessionId.isNotBlank()) { "The server returned an invalid session ID." }
                pendingSession = session
                _loginState.value = LoginState.AwaitingTarget(
                    session = session,
                    isLoadingTargets = true
                )
                sessionStore.save(session.sessionId)

                val statusResponse = api.getStatus()
                if (!statusResponse.isSuccessful) {
                    throw ApiRequestException(responseError(statusResponse))
                }
                val status = statusResponse.body()
                    ?: throw IOException("The server returned an empty status response.")
                _loginState.value = LoginState.AwaitingTarget(
                    session = session,
                    targets = status.targets
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val error = exceptionMessage(exception)
                val currentState = _loginState.value as? LoginState.AwaitingTarget
                _loginState.value = if (pendingSession != null && currentState != null) {
                    currentState.copy(isLoadingTargets = false, error = error)
                } else {
                    LoginState.Failure(error)
                }
            }
        }
    }

    fun selectTarget(rawEndpoint: String, target: RemoteTarget) {
        val state = _loginState.value as? LoginState.AwaitingTarget ?: return
        viewModelScope.launch {
            _loginState.value = state.copy(isBindingTarget = true, error = null)
            try {
                require(state.targets.any { it == target }) {
                    "Refresh the window list and select an available game window."
                }
                val api = ApiConfig.createApiService(saveEndpoint(rawEndpoint))
                val response = api.bindTarget(
                    state.session.sessionId,
                    ApiConfig.sessionAuthorization(state.session.sessionId),
                    BindTargetRequest(target.hwnd)
                )
                if (!response.isSuccessful) {
                    throw ApiRequestException(responseError(response))
                }
                val boundSession = response.body()
                    ?: throw IOException("The server returned an empty session response.")
                require(boundSession.sessionId == state.session.sessionId) {
                    "The server returned an unexpected session."
                }
                require(boundSession.target == target) {
                    "The server bound the session to a different game window."
                }
                _loginState.value = LoginState.Success(boundSession)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val currentState = _loginState.value as? LoginState.AwaitingTarget
                if (currentState != null) {
                    _loginState.value = currentState.copy(
                        isBindingTarget = false,
                        error = exceptionMessage(exception)
                    )
                }
            }
        }
    }

    fun cancelTargetSelection(rawEndpoint: String) {
        val state = _loginState.value as? LoginState.AwaitingTarget ?: return
        viewModelScope.launch {
            _loginState.value = state.copy(isCancellingSession = true, error = null)
            try {
                val response = ApiConfig.createApiService(saveEndpoint(rawEndpoint))
                    .closeSession(
                        state.session.sessionId,
                        ApiConfig.sessionAuthorization(state.session.sessionId)
                    )
                if (!response.isSuccessful) {
                    throw ApiRequestException(responseError(response))
                }
                _loginState.value = LoginState.Idle
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val currentState = _loginState.value as? LoginState.AwaitingTarget
                if (currentState != null) {
                    _loginState.value = currentState.copy(
                        isCancellingSession = false,
                        error = exceptionMessage(exception)
                    )
                }
            }
        }
    }

    fun sessionClosed() {
        _loginState.value = LoginState.Idle
    }

    private fun updateTargetSelectionError(error: String) {
        val state = _loginState.value as? LoginState.AwaitingTarget ?: return
        _loginState.value = state.copy(isLoadingTargets = false, error = error)
    }

    private fun saveEndpoint(rawEndpoint: String): String {
        val normalized = ApiConfig.normalizeBaseUrl(rawEndpoint)
        if (_endpoint.value != normalized) {
            check(preferences.edit().putString(ENDPOINT_KEY, normalized).commit()) {
                "Could not save the server URL."
            }
            _endpoint.value = normalized
        }
        return normalized
    }

    private fun loadSavedEndpoint(): String {
        val savedEndpoint = preferences.getString(ENDPOINT_KEY, null)
            ?: return ApiConfig.DEFAULT_BASE_URL
        if (savedEndpoint == PREVIOUS_BASE_URL || savedEndpoint == PREVIOUS_BASE_URL_WITHOUT_SLASH) {
            check(preferences.edit().putString(ENDPOINT_KEY, ApiConfig.DEFAULT_BASE_URL).commit()) {
                "Could not update the saved server URL."
            }
            return ApiConfig.DEFAULT_BASE_URL
        }
        return savedEndpoint
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
        data class AwaitingTarget(
            val session: HandshakeResponse,
            val targets: List<RemoteTarget> = emptyList(),
            val isLoadingTargets: Boolean = false,
            val isBindingTarget: Boolean = false,
            val isCancellingSession: Boolean = false,
            val error: String? = null
        ) : LoginState()
        data class Success(val session: HandshakeResponse) : LoginState()
        data class Failure(val message: String) : LoginState()
    }

    private class ApiRequestException(message: String) : IOException(message)

    private companion object {
        const val PREFERENCES_NAME = "remote-client"
        const val ENDPOINT_KEY = "server-endpoint"
        const val REMEMBER_CREDENTIALS_KEY = "remember-credentials"
        const val PREVIOUS_BASE_URL = "http://192.168.1.25:8765/"
        const val PREVIOUS_BASE_URL_WITHOUT_SLASH = "http://192.168.1.25:8765"
    }
}
