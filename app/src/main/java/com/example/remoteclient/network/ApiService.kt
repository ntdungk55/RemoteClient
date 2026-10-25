package com.example.remoteclient.network

import com.example.remoteclient.model.*
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    @GET("/status")
    fun getStatus(): Call<ServerStatus>

    @POST("/handshake")
    fun handshake(@Body credentials: RemoteCredentials): Call<HandshakeResponse>

    @POST("/sessions/{id}/close")
    fun closeSession(@Path("id") sessionId: String): Call<Void>
}
