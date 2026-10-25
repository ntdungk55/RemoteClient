package com.example.remoteclient.network

import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.model.HandshakeResponse
import com.example.remoteclient.model.ServerStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @GET("status")
    suspend fun getStatus(): Response<ServerStatus>

    @POST("handshake")
    suspend fun handshake(@Body request: HandshakeRequest): Response<HandshakeResponse>

    @POST("sessions/{id}/close")
    suspend fun closeSession(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
