package com.example.remoteclient.network

import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiConfig {
    const val DEFAULT_BASE_URL = "http://192.168.1.4:8765/"
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    fun normalizeBaseUrl(value: String): String {
        val input = value.trim()
        val uri = try {
            URI(input)
        } catch (exception: URISyntaxException) {
            throw IllegalArgumentException("Enter a valid server URL.", exception)
        }
        require(uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) {
            "Server URL must use HTTP or HTTPS."
        }
        require(uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Enter a server URL with a host and no credentials, query, or fragment."
        }
        val path = uri.path.orEmpty()
        val normalizedPath = if (path.endsWith("/")) path else "$path/"
        return try {
            URI(uri.scheme, uri.rawAuthority, normalizedPath, null, null).toASCIIString()
        } catch (exception: URISyntaxException) {
            throw IllegalArgumentException("Enter a valid server URL.", exception)
        }
    }

    fun createApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(createHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun createHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun websocketBaseUrl(baseUrl: String): String =
        normalizeBaseUrl(baseUrl)
            .replaceFirst(Regex("^https://", RegexOption.IGNORE_CASE), "wss://")
            .replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "ws://")

    fun sessionAuthorization(sessionId: String): String {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
        return "Bearer $sessionId"
    }
}
