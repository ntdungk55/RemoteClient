package com.example.remoteclient.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

class GameStreamClient(
    private val client: OkHttpClient = ApiConfig.createHttpClient()
) {
    fun connect(
        baseUrl: String,
        sessionId: String,
        authorization: String,
        onFrame: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ): WebSocket {
        val url = "${ApiConfig.websocketBaseUrl(baseUrl)}sessions/$sessionId/stream"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .build()
        return client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
                    if (bitmap == null) {
                        onError("The server sent an invalid game frame.")
                    } else {
                        onFrame(bitmap)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = try {
                        JSONObject(text)
                    } catch (_: Exception) {
                        return
                    }
                    when (message.optString("type")) {
                        "session_revoked" -> onError("The server revoked this session.")
                        "target_unavailable" -> onError("The selected game window is no longer available.")
                        "game_state" -> Unit
                    }
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    val detail = response?.let { " (HTTP ${it.code()})" }.orEmpty()
                    onError("Game stream failed$detail: ${throwable.message ?: "connection error"}")
                }
            }
        )
    }
}
