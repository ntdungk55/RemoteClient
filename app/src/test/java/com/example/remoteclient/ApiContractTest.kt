package com.example.remoteclient

import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.network.ApiConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiContractTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun normalizesBaseUrlForRetrofit() {
        assertEquals(
            "https://example.test:8765/remote/",
            ApiConfig.normalizeBaseUrl("https://example.test:8765/remote")
        )
    }

    @Test
    fun rejectsUnsupportedSchemesAndMalformedBaseUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfig.normalizeBaseUrl("ftp://example.test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfig.normalizeBaseUrl("http://user:pass@example.test")
        }
    }

    @Test
    fun rejectsBaseUrlsWithQueryParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfig.normalizeBaseUrl("http://example.test/path?query=value")
        }
    }

    @Test
    fun parsesStatusTargetsAndHandshakePermissions() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"status":"running","endpoint":"http://127.0.0.1:8765","sessions":1,"targets":[{"hwnd":12345,"pid":678,"window_title":"Game","process_name":"game.exe","creation_time":"2026-10-25T10:00:00Z"}]}"""
            )
        )
        server.enqueue(
            jsonResponse(
                """{"session_id":"session-1","device_name":"Android","target":{"hwnd":12345,"pid":678,"window_title":"Game","process_name":"game.exe","creation_time":"2026-10-25T10:00:00Z"},"connected_at":"2026-10-25T10:01:00Z","permissions":{"connect":true,"view_stream":true,"view_game_state":true,"send_chat":false,"discover_tools":true,"call_readonly_tools":true,"call_mutating_tools":false}}"""
            )
        )
        val api = ApiConfig.createApiService(server.url("/").toString())

        val statusResponse = api.getStatus()
        val status = statusResponse.body()
        assertTrue(statusResponse.isSuccessful)
        assertNotNull(status)
        assertEquals(1, status!!.sessions)
        assertEquals(12345L, status.targets.single().hwnd)
        assertEquals("2026-10-25T10:00:00Z", status.targets.single().creationTime)

        val handshakeResponse = api.handshake(
            HandshakeRequest("1234", "5678", "Android", 12345L)
        )
        val session = handshakeResponse.body()
        assertTrue(handshakeResponse.isSuccessful)
        assertNotNull(session)
        assertEquals(12345L, session!!.target.hwnd)
        assertTrue(session.permissions.viewStream)
        assertFalse(session.permissions.sendChat)
        assertFalse(session.permissions.callMutatingTools)

        assertEquals("/status", server.takeRequest().path)
        val handshakeRequest = server.takeRequest()
        assertEquals("/handshake", handshakeRequest.path)
        val body = JsonParser().parse(handshakeRequest.body.readUtf8()).asJsonObject
        assertEquals("1234", body.get("username").asString)
        assertEquals("5678", body.get("password").asString)
        assertEquals("Android", body.get("device_name").asString)
        assertEquals(12345L, body.get("target_hwnd").asLong)
    }

    @Test
    fun reportsAuthenticationAndUnavailableTargetHttpStatuses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"invalid_credentials"}"""))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"target_unavailable"}"""))
        val api = ApiConfig.createApiService(server.url("/").toString())
        val request = HandshakeRequest("bad", "credentials", "Android", 12345L)

        assertEquals(401, api.handshake(request).code())
        assertEquals(409, api.handshake(request).code())
    }

    @Test
    fun closeSessionSendsRequiredAuthorization() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"closed":true}""")
        )
        val response = ApiConfig.createApiService(server.url("/").toString())
            .closeSession("session-1", ApiConfig.CLOSE_SESSION_AUTHORIZATION)

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("/sessions/session-1/close", request.path)
        assertEquals(ApiConfig.CLOSE_SESSION_AUTHORIZATION, request.getHeader("Authorization"))
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
