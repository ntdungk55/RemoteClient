package com.example.remoteclient.model

import com.google.gson.annotations.SerializedName

// Represents the server state
enum class RemoteServerState {
    STOPPED, STARTING, RUNNING, STOPPING, ERROR
}

// Represents the server status response
data class ServerStatus(
    @SerializedName("state") val state: RemoteServerState,
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("sessions") val sessions: List<RemoteSession>
)

// Represents a remote session
data class RemoteSession(
    @SerializedName("id") val id: String,
    @SerializedName("target") val target: RemoteTarget
)

// Represents a remote target (e.g., a window or process)
data class RemoteTarget(
    @SerializedName("hwnd") val hwnd: Int,
    @SerializedName("pid") val pid: Int,
    @SerializedName("window_title") val windowTitle: String,
    @SerializedName("process_name") val processName: String
)

// Represents credentials for authentication
data class RemoteCredentials(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

// Represents the handshake response
data class HandshakeResponse(
    @SerializedName("session_id") val sessionId: String
)
