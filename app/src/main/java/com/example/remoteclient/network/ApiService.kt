package com.example.remoteclient.network

import com.example.remoteclient.model.BindTargetRequest
import com.example.remoteclient.model.ChatHistoryResponse
import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.SendChatMessageRequest
import com.example.remoteclient.model.ServerStatus
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("status")
    suspend fun getStatus(): Response<ServerStatus>

    @POST("handshake")
    suspend fun handshake(@Body request: HandshakeRequest): Response<HandshakeResponse>

    @POST("sessions/{id}/target")
    suspend fun bindTarget(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body request: BindTargetRequest
    ): Response<HandshakeResponse>

    @GET("sessions/{id}/messages")
    suspend fun getChatMessages(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 10
    ): Response<ChatHistoryResponse>

    @POST("sessions/{id}/messages")
    suspend fun sendChatMessage(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body request: SendChatMessageRequest
    ): Response<Unit>

    @GET("sessions/{id}/frame")
    suspend fun getGameFrame(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @POST("sessions/{id}/close")
    suspend fun closeSession(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
