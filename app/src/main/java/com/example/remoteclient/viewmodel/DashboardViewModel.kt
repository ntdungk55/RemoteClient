package com.example.remoteclient.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteclient.model.BindTargetRequest
import com.example.remoteclient.model.RemoteChatMessage
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.RemoteTarget
import com.example.remoteclient.model.SendChatMessageRequest
import com.example.remoteclient.model.ServerError
import com.example.remoteclient.network.ApiConfig
import com.example.remoteclient.network.GameStreamClient
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
import okhttp3.WebSocket

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SecureSessionStore(application)
    private var gameStream: WebSocket? = null

    private val _isClosing = MutableStateFlow(false)
    val isClosing: StateFlow<Boolean> = _isClosing

    private val _closedSessionId = MutableStateFlow<String?>(null)
    val closedSessionId: StateFlow<String?> = _closedSessionId

    private val _messages = MutableStateFlow<List<RemoteChatMessage>>(emptyList())
    val messages: StateFlow<List<RemoteChatMessage>> = _messages

    private val _hasMoreMessages = MutableStateFlow(false)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages

    private val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages

    private val _activeSession = MutableStateFlow<HandshakeResponse?>(null)
    val activeSession: StateFlow<HandshakeResponse?> = _activeSession

    private val _availableTargets = MutableStateFlow<List<RemoteTarget>>(emptyList())
    val availableTargets: StateFlow<List<RemoteTarget>> = _availableTargets

    private val _isLoadingTargets = MutableStateFlow(false)
    val isLoadingTargets: StateFlow<Boolean> = _isLoadingTargets

    private val _isChangingTarget = MutableStateFlow(false)
    val isChangingTarget: StateFlow<Boolean> = _isChangingTarget

    private val _targetError = MutableStateFlow<String?>(null)
    val targetError: StateFlow<String?> = _targetError

    private var loadedMessageCount = 0

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError

    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage

    private val _gameFrame = MutableStateFlow<Bitmap?>(null)
    val gameFrame: StateFlow<Bitmap?> = _gameFrame

    private val _isLoadingFrame = MutableStateFlow(false)
    val isLoadingFrame: StateFlow<Boolean> = _isLoadingFrame

    private val _frameError = MutableStateFlow<String?>(null)
    val frameError: StateFlow<String?> = _frameError

    fun refreshMessages(baseUrl: String, sessionId: String) {
        if (_isLoadingMessages.value) return
        _isLoadingMessages.value = true
        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl).getChatMessages(
                    sessionId,
                    ApiConfig.sessionAuthorization(sessionId),
                    offset = 0,
                    limit = PAGE_SIZE
                )
                if (!response.isSuccessful) throw IOException(responseError(response))
                val latestMessages = response.body()
                    ?: throw IOException("The server returned an empty chat history.")
                val existingIds = _messages.value.mapTo(mutableSetOf()) { it.id }
                val newMessages = latestMessages.messages.filterNot { it.id in existingIds }
                _messages.value = (_messages.value + newMessages).sortedBy { it.timestamp }
                loadedMessageCount += newMessages.size
                _hasMoreMessages.value = latestMessages.hasMore
                _chatError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _chatError.value = exceptionMessage(exception)
            } finally {
                _isLoadingMessages.value = false
            }
        }
    }

    fun loadOlderMessages(baseUrl: String, sessionId: String) {
        if (_isLoadingOlderMessages.value || !_hasMoreMessages.value) return
        _isLoadingOlderMessages.value = true
        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl).getChatMessages(
                    sessionId,
                    ApiConfig.sessionAuthorization(sessionId),
                    offset = loadedMessageCount,
                    limit = PAGE_SIZE
                )
                if (!response.isSuccessful) throw IOException(responseError(response))
                val page = response.body()
                    ?: throw IOException("The server returned an empty chat history.")
                val existingIds = _messages.value.mapTo(mutableSetOf()) { it.id }
                val olderMessages = page.messages.filterNot { it.id in existingIds }
                _messages.value = (olderMessages + _messages.value).sortedBy { it.timestamp }
                loadedMessageCount += olderMessages.size
                _hasMoreMessages.value = page.hasMore
                _chatError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _chatError.value = exceptionMessage(exception)
            } finally {
                _isLoadingOlderMessages.value = false
            }
        }
    }

    fun setActiveSession(session: HandshakeResponse) {
        if (_activeSession.value?.sessionId == session.sessionId) return
        _activeSession.value = session
        _messages.value = emptyList()
        _hasMoreMessages.value = false
        loadedMessageCount = 0
    }

    fun loadTargets(baseUrl: String) {
        if (_isLoadingTargets.value) return
        _isLoadingTargets.value = true
        _targetError.value = null
        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl).getStatus()
                if (!response.isSuccessful) throw IOException(responseError(response))
                val status = response.body()
                    ?: throw IOException("The server returned an empty status response.")
                _availableTargets.value = status.targets
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _targetError.value = exceptionMessage(exception)
            } finally {
                _isLoadingTargets.value = false
            }
        }
    }

    fun changeTarget(baseUrl: String, target: RemoteTarget) {
        val session = _activeSession.value ?: return
        if (_isChangingTarget.value || session.target == target) return
        _isChangingTarget.value = true
        _targetError.value = null
        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl).bindTarget(
                    session.sessionId,
                    ApiConfig.sessionAuthorization(session.sessionId),
                    BindTargetRequest(target.hwnd)
                )
                if (!response.isSuccessful) throw IOException(responseError(response))
                val updatedSession = response.body()
                    ?: throw IOException("The server returned an empty session response.")
                require(updatedSession.sessionId == session.sessionId) {
                    "The server returned an unexpected session."
                }
                require(updatedSession.target == target) {
                    "The server bound the session to a different game window."
                }
                stopGameStream()
                _gameFrame.value = null
                _messages.value = emptyList()
                _hasMoreMessages.value = false
                loadedMessageCount = 0
                _activeSession.value = updatedSession
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _targetError.value = exceptionMessage(exception)
            } finally {
                _isChangingTarget.value = false
            }
        }
    }

    fun sendMessage(baseUrl: String, sessionId: String, text: String) {
        val message = text.trim()
        if (message.isEmpty() || _isSendingMessage.value) return
        _isSendingMessage.value = true
        viewModelScope.launch {
            _chatError.value = null
            try {
                val response = ApiConfig.createApiService(baseUrl)
                    .sendChatMessage(
                        sessionId,
                        ApiConfig.sessionAuthorization(sessionId),
                        SendChatMessageRequest(message)
                    )
                if (!response.isSuccessful) throw IOException(responseError(response))
                refreshMessagesNow(baseUrl, sessionId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _chatError.value = exceptionMessage(exception)
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun refreshGameFrame(baseUrl: String, sessionId: String) {
        if (_isLoadingFrame.value) return
        _isLoadingFrame.value = true
        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl).getGameFrame(
                    sessionId,
                    ApiConfig.sessionAuthorization(sessionId)
                )
                if (!response.isSuccessful) throw IOException(responseError(response))
                val body = response.body() ?: throw IOException("The server returned an empty game frame.")
                val bytes = body.use { it.bytes() }
                _gameFrame.value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IOException("The server returned an invalid game frame.")
                _frameError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _frameError.value = exceptionMessage(exception)
            } finally {
                _isLoadingFrame.value = false
            }
        }
    }

    private suspend fun refreshMessagesNow(baseUrl: String, sessionId: String) {
        val response = ApiConfig.createApiService(baseUrl).getChatMessages(
            sessionId,
            ApiConfig.sessionAuthorization(sessionId),
            offset = 0,
            limit = PAGE_SIZE
        )
        if (!response.isSuccessful) throw IOException(responseError(response))
        val latestMessages = response.body()
            ?: throw IOException("The server returned an empty chat history.")
        val existingIds = _messages.value.mapTo(mutableSetOf()) { it.id }
        val newMessages = latestMessages.messages.filterNot { it.id in existingIds }
        _messages.value = (_messages.value + newMessages).sortedBy { it.timestamp }
        loadedMessageCount += newMessages.size
        _hasMoreMessages.value = latestMessages.hasMore
    }

    fun startGameStream(baseUrl: String, sessionId: String) {
        if (gameStream != null) return
        _frameError.value = null
        gameStream = GameStreamClient().connect(
            baseUrl = baseUrl,
            sessionId = sessionId,
            authorization = ApiConfig.sessionAuthorization(sessionId),
            onFrame = { bitmap ->
                _gameFrame.value = bitmap
                _frameError.value = null
            },
            onError = { error ->
                _frameError.value = error
            }
        )
    }

    fun stopGameStream() {
        gameStream?.close(1000, "Game view closed")
        gameStream = null
    }

    fun closeSession(baseUrl: String, sessionId: String) {
        if (_isClosing.value) return
        _isClosing.value = true
        _closedSessionId.value = null
        stopGameStream()
        try {
            sessionStore.clear()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not clear the locally stored session (${exception.javaClass.simpleName}).")
        }
        _closedSessionId.value = sessionId

        viewModelScope.launch {
            try {
                val response = ApiConfig.createApiService(baseUrl)
                    .closeSession(sessionId, ApiConfig.sessionAuthorization(sessionId))
                if (!response.isSuccessful) {
                    Log.w(TAG, "Server did not close the session (HTTP ${response.code()}).")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Could not notify the server to close the session (${exception.javaClass.simpleName}).")
            } finally {
                _isClosing.value = false
            }
        }
    }

    override fun onCleared() {
        stopGameStream()
        super.onCleared()
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
            else -> "Request failed"
        }
        return listOfNotNull(description, "HTTP ${response.code()}", code, detail)
            .joinToString(": ")
    }

    private fun exceptionMessage(exception: Exception): String = when (exception) {
        is SocketTimeoutException -> "The server timed out. Try again."
        is UnknownHostException -> "Server host could not be reached. Check the URL and network."
        is IOException -> exception.message ?: "Network request failed."
        is IllegalArgumentException -> exception.message ?: "Check the server URL."
        else -> "Network request failed: ${exception.message ?: exception.javaClass.simpleName}"
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val TAG = "DashboardViewModel"
    }
}
