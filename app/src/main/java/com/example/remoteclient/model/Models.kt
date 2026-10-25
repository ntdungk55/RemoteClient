package com.example.remoteclient.model

import com.google.gson.annotations.SerializedName

data class ServerStatus(
    @SerializedName("status") val status: String,
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("sessions") val sessions: Int,
    @SerializedName("targets") val targets: List<RemoteTarget>
)

data class RemoteTarget(
    @SerializedName("hwnd") val hwnd: Long,
    @SerializedName("pid") val pid: Long,
    @SerializedName("window_title") val windowTitle: String,
    @SerializedName("process_name") val processName: String,
    @SerializedName("creation_time") val creationTime: String
)

data class HandshakeRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("target_hwnd") val targetHwnd: Long
)

data class HandshakeResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("target") val target: RemoteTarget,
    @SerializedName("connected_at") val connectedAt: String,
    @SerializedName("permissions") val permissions: RemotePermissions
)

data class RemotePermissions(
    @SerializedName("connect") val connect: Boolean,
    @SerializedName("view_stream") val viewStream: Boolean,
    @SerializedName("view_game_state") val viewGameState: Boolean,
    @SerializedName("send_chat") val sendChat: Boolean,
    @SerializedName("discover_tools") val discoverTools: Boolean,
    @SerializedName("call_readonly_tools") val callReadonlyTools: Boolean,
    @SerializedName("call_mutating_tools") val callMutatingTools: Boolean
)

data class ServerError(
    @SerializedName("code") val code: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("detail") val detail: String?
)
