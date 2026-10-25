package com.example.remoteclient

import com.example.remoteclient.model.BindTargetRequest
import com.example.remoteclient.model.SendChatMessageRequest
import com.example.remoteclient.model.HandshakeRequest
import com.example.remoteclient.network.ApiConfig
import com.example.remoteclient.network.GameStreamClient
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        assertEquals("http://192.168.1.4:8765/", ApiConfig.DEFAULT_BASE_URL)
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
                """{"session_id":"session-1","device_name":"Android","target":null,"connected_at":"2026-10-25T10:01:00Z","permissions":{"connect":true,"view_stream":true,"view_game_state":true,"send_chat":false,"discover_tools":true,"call_readonly_tools":true,"call_mutating_tools":false}}"""
            )
        )
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

        val handshakeResponse = api.handshake(HandshakeRequest("1234", "5678", "Android"))
        val pendingSession = handshakeResponse.body()
        assertTrue(handshakeResponse.isSuccessful)
        assertNotNull(pendingSession)
        assertEquals(null, pendingSession!!.target)

        val statusResponse = api.getStatus()
        val status = statusResponse.body()
        assertTrue(statusResponse.isSuccessful)
        assertNotNull(status)
        assertEquals(1, status!!.sessions)
        assertEquals(12345L, status.targets.single().hwnd)
        assertEquals("2026-10-25T10:00:00Z", status.targets.single().creationTime)

        val bindResponse = api.bindTarget(
            pendingSession.sessionId,
            ApiConfig.sessionAuthorization(pendingSession.sessionId),
            BindTargetRequest(12345L)
        )
        val session = bindResponse.body()
        assertTrue(bindResponse.isSuccessful)
        assertNotNull(session)
        assertEquals(12345L, session!!.target?.hwnd)
        assertTrue(session.permissions.viewStream)
        assertFalse(session.permissions.sendChat)
        assertFalse(session.permissions.callMutatingTools)

        val handshakeRequest = server.takeRequest()
        assertEquals("/handshake", handshakeRequest.path)
        val body = JsonParser().parse(handshakeRequest.body.readUtf8()).asJsonObject
        assertEquals("1234", body.get("username").asString)
        assertEquals("5678", body.get("password").asString)
        assertEquals("Android", body.get("device_name").asString)
        assertFalse(body.has("target_hwnd"))
        assertEquals("/status", server.takeRequest().path)
        val bindRequest = server.takeRequest()
        assertEquals("/sessions/session-1/target", bindRequest.path)
        assertEquals("Bearer session-1", bindRequest.getHeader("Authorization"))
        assertEquals(
            12345L,
            JsonParser().parse(bindRequest.body.readUtf8()).asJsonObject.get("target_hwnd").asLong
        )
    }

    @Test
    fun reportsAuthenticationAndUnavailableTargetHttpStatuses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"invalid_credentials"}"""))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"target_unavailable"}"""))
        val api = ApiConfig.createApiService(server.url("/").toString())
        val request = HandshakeRequest("bad", "credentials", "Android")

        assertEquals(401, api.handshake(request).code())
        assertEquals(
            409,
            api.bindTarget(
                "session-1",
                ApiConfig.sessionAuthorization("session-1"),
                BindTargetRequest(12345L)
            ).code()
        )
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
            .closeSession("session-1", ApiConfig.sessionAuthorization("session-1"))

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("/sessions/session-1/close", request.path)
        assertEquals("Bearer session-1", request.getHeader("Authorization"))
    }

    @Test
    fun chatAndGameViewUseTheBoundSessionEndpoints() = runBlocking {
        server.enqueue(jsonResponse("""{"messages":[{"id":"turn-1:right","turn_id":"turn-1","side":"right","text":"Hello game","timestamp":"2026-10-25T10:00:00Z"}],"has_more":true}"""))
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"accepted":true}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(byteArrayOf(1, 2, 3)))
        )
        val api = ApiConfig.createApiService(server.url("/").toString())

        val history = api.getChatMessages("session-1", ApiConfig.sessionAuthorization("session-1"))
        assertTrue(history.isSuccessful)
        assertEquals("Hello game", history.body()!!.messages.single().text)
        assertTrue(history.body()!!.hasMore)

        val send = api.sendChatMessage(
            "session-1",
            ApiConfig.sessionAuthorization("session-1"),
            SendChatMessageRequest("Open inventory")
        )
        assertTrue(send.isSuccessful)

        val frame = api.getGameFrame("session-1", ApiConfig.sessionAuthorization("session-1"))
        assertTrue(frame.isSuccessful)
        assertEquals(listOf<Byte>(1, 2, 3), frame.body()!!.bytes().toList())

        val historyRequest = server.takeRequest()
        assertEquals("/sessions/session-1/messages?offset=0&limit=10", historyRequest.path)
        assertEquals("Bearer session-1", historyRequest.getHeader("Authorization"))
        val sendRequest = server.takeRequest()
        assertEquals("/sessions/session-1/messages", sendRequest.path)
        assertEquals("Bearer session-1", sendRequest.getHeader("Authorization"))
        assertEquals("Open inventory", JsonParser().parse(sendRequest.body.readUtf8()).asJsonObject.get("text").asString)
        val frameRequest = server.takeRequest()
        assertEquals("/sessions/session-1/frame", frameRequest.path)
        assertEquals("Bearer session-1", frameRequest.getHeader("Authorization"))
    }

    @Test
    fun gameStreamHandshakeSendsSessionAuthorization() {
        val opened = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        opened.countDown()
                        webSocket.close(1000, "test complete")
                    }
                }
            )
        )

        val socket = GameStreamClient().connect(
            server.url("/").toString(),
            "session-1",
            ApiConfig.sessionAuthorization("session-1"),
            onFrame = {},
            onError = {}
        )
        try {
            assertTrue("WebSocket did not connect", opened.await(3, TimeUnit.SECONDS))
            val request = server.takeRequest()
            assertEquals("/sessions/session-1/stream", request.path)
            assertEquals("websocket", request.getHeader("Upgrade"))
            assertEquals("Bearer session-1", request.getHeader("Authorization"))
        } finally {
            socket.cancel()
        }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
