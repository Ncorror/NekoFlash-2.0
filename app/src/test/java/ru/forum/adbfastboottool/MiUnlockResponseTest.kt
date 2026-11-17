package ru.forum.adbfastboottool

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiUnlockResponseTest {
    @Test
    fun readsExplicitWipeFlags() {
        assertEquals(
            MiUnlockClient.ClearInfo("Back up your files", true),
            MiUnlockClient.parseClearInfo(JSONObject("""{"cleanOrNot":1,"notice":"Back up your files"}"""))
        )
        assertEquals(false, MiUnlockClient.parseClearInfo(JSONObject("""{"cleanOrNot":"0"}""")).clearsData)
    }

    @Test
    fun missingOrInvalidWipeFlagRemainsUnknown() {
        for (response in listOf("{}", """{"cleanOrNot":null}""", """{"cleanOrNot":2}""")) {
            assertNull(MiUnlockClient.parseClearInfo(JSONObject(response)).clearsData)
        }
    }

    @Test
    fun readsExplicitWaitingHours() {
        assertEquals(168, MiUnlockClient.parseWaitHours(JSONObject("""{"data":{"waitHour":168}}""")))
        assertEquals(72, MiUnlockClient.parseWaitHours(JSONObject("""{"data":"72"}""")))
        assertEquals(0, MiUnlockClient.parseWaitHours(JSONObject("""{"data":{"waitHour":0}}""")))
    }

    @Test
    fun malformedOrMissingWaitingHoursRemainUnknown() {
        for (response in listOf(
            """{"descEN":"Xiaomi code 20036"}""",
            """{"data":{"waitHour":-1}}""",
            """{"data":{"waitHour":1.5}}""",
            """{"data":{"waitHour":"later"}}"""
        )) {
            assertNull(MiUnlockClient.parseWaitHours(JSONObject(response)))
        }
    }

    private fun withUnlockReply(reply: String, status: String = "200 OK", check: (MiUnlockClient) -> Unit) {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            server.soTimeout = 5_000
            val requests = FutureTask {
                List(2) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val request = URI.create(input.readLine().split(" ")[1])
                        while (!input.readLine().isNullOrEmpty()) { /* Consume headers. */ }
                        val nonce = request.path == "/api/v2/nonce"
                        val body = (if (nonce) """{"nonce":"single-use-nonce"}""" else reply).toByteArray()
                        val httpStatus = if (nonce) "200 OK" else status
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 $httpStatus\r\nContent-Length: ${body.size}\r\n" +
                                "Content-Type: application/json\r\nConnection: close\r\n\r\n")
                                .toByteArray(Charsets.US_ASCII))
                            write(body)
                        }
                        request.path
                    }
                }
            }
            thread(isDaemon = true, name = "mi-unlock-response-test") { requests.run() }
            check(MiUnlockClient("http://127.0.0.1:${server.localPort}",
                "AAAAAAAAAAAAAAAAAAAAAA==", "", "test-account", "test-client"))
            assertEquals(listOf("/api/v2/nonce", "/api/v3/ahaUnlock"), requests.get(5, TimeUnit.SECONDS))
        }
    }

    @Test fun businessRefusalPreservesCodeExactServerTextAndWaitingHours() {
        withUnlockReply("""{"code":20036,"descEN":"Please unlock 164 hours later","data":{"waitHour":164}}""") { client ->
            val error = assertThrows(MiUnlockClient.BusinessException::class.java) {
                client.requestUnlock("marble", "test-token")
            }
            assertEquals(20036, error.code)
            assertEquals(164, error.waitHours)
            assertEquals("Please unlock 164 hours later", error.serverText)
            org.junit.Assert.assertTrue(error.message!!.contains("Please unlock 164 hours later"))
        }
    }

    @Test fun regionBindingAndUnknownRefusalsRetainTheServerReason() {
        for ((code, text) in listOf(20045 to "Region mismatch", 20031 to "Bind this account to the device",
            29999 to "Future Xiaomi reason, unchanged")) {
            withUnlockReply(JSONObject().put("code", code).put("message", text).toString()) { client ->
                val error = assertThrows(MiUnlockClient.BusinessException::class.java) {
                    client.requestUnlock("marble", "test-token")
                }
                assertEquals(code, error.code)
                assertEquals(text, error.serverText)
                assertNull(error.waitHours)
                org.junit.Assert.assertTrue(error.message!!.contains(text))
            }
        }
    }

    @Test fun expiredSessionRetainsHttpStatusAndDoesNotReplayUnlock() {
        withUnlockReply("""{"message":"Session revoked"}""", "401 Unauthorized") { client ->
            val error = assertThrows(MiUnlockClient.SessionExpiredException::class.java) {
                client.requestUnlock("marble", "test-token")
            }
            org.junit.Assert.assertTrue(error.message!!.contains("HTTP 401"))
            org.junit.Assert.assertTrue(error.message!!.contains("Session revoked"))
        }
    }

    @Test fun serverSuccessWithoutUnlockDataIsAnError() {
        withUnlockReply("""{"code":0}""") { client ->
            val error = assertThrows(java.io.IOException::class.java) {
                client.requestUnlock("marble", "test-token")
            }
            org.junit.Assert.assertTrue(error.message!!.contains("encryptData empty"))
        }
    }

    @Test
    fun clearRetryAndUnlockEachUseTheirOwnNonce() {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            server.soTimeout = 5_000
            val requests = FutureTask {
                var nonceCount = 0
                var clearCount = 0
                List(6) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val request = URI.create(input.readLine().split(" ")[1])
                        while (!input.readLine().isNullOrEmpty()) {
                            // Consume the request headers before responding.
                        }
                        val (status, response) = when (request.path) {
                            "/api/v2/nonce" -> "200 OK" to """{"nonce":"test-nonce-${++nonceCount}"}"""
                            "/api/v2/unlock/device/clear" ->
                                if (++clearCount == 1) "500 Internal Server Error" to "{}"
                                else "200 OK" to """{"cleanOrNot":1}"""
                            "/api/v3/ahaUnlock" -> "200 OK" to """{"code":20036,"data":{"waitHour":168}}"""
                            else -> "404 Not Found" to "{}"
                        }
                        val body = response.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(
                                ("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\n" +
                                    "Content-Type: application/json\r\nConnection: close\r\n\r\n")
                                    .toByteArray(Charsets.US_ASCII)
                            )
                            write(body)
                        }
                        val nonce = request.rawQuery.orEmpty().split("&").firstOrNull { it.startsWith("nonce=") }
                        request.path to nonce
                    }
                }
            }
            thread(isDaemon = true, name = "mi-unlock-test-server") { requests.run() }

            val client = MiUnlockClient(
                host = "http://127.0.0.1:${server.localPort}",
                ssecurity = "AAAAAAAAAAAAAAAAAAAAAA==",
                serviceCookies = "",
                userId = "test-account",
                deviceId = "test-client"
            )
            assertEquals(true, client.checkClear("test-product").clearsData)
            val result = assertThrows(MiUnlockClient.BusinessException::class.java) {
                client.requestUnlock("test-product", "test-device-token")
            }
            assertEquals(20036, result.code)
            assertEquals(168, result.waitHours)
            val captured = requests.get(5, TimeUnit.SECONDS)
            assertEquals(
                listOf(
                    "/api/v2/nonce", "/api/v2/unlock/device/clear",
                    "/api/v2/nonce", "/api/v2/unlock/device/clear",
                    "/api/v2/nonce", "/api/v3/ahaUnlock"
                ),
                captured.map { it.first }
            )
            val firstClearNonce = captured[1].second
            val retryClearNonce = captured[3].second
            val unlockNonce = captured[5].second
            listOf(firstClearNonce, retryClearNonce, unlockNonce).forEach { assertNotNull(it) }
            assertNotEquals(firstClearNonce, retryClearNonce)
            assertNotEquals(firstClearNonce, unlockNonce)
            assertNotEquals(retryClearNonce, unlockNonce)
        }
    }
}
