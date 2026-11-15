package ru.forum.adbfastboottool

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun clearRetryAndUnlockEachUseTheirOwnNonce() {
        val requests = CopyOnWriteArrayList<Pair<String, String?>>()
        val nonceCount = AtomicInteger()
        val clearCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val nonce = exchange.requestURI.rawQuery.orEmpty().split("&")
                .firstOrNull { it.startsWith("nonce=") }
            requests += path to nonce

            val (status, response) = when (path) {
                "/api/v2/nonce" -> 200 to """{"nonce":"test-nonce-${nonceCount.incrementAndGet()}"}"""
                "/api/v2/unlock/device/clear" ->
                    if (clearCount.incrementAndGet() == 1) 500 to "{}" else 200 to """{"cleanOrNot":1}"""
                "/api/v3/ahaUnlock" -> 200 to """{"code":20036,"data":{"waitHour":168}}"""
                else -> 404 to "{}"
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val client = MiUnlockClient(
                host = "http://127.0.0.1:${server.address.port}",
                ssecurity = "AAAAAAAAAAAAAAAAAAAAAA==",
                serviceCookies = "",
                userId = "test-account",
                deviceId = "test-client"
            )
            assertEquals(true, client.checkClear("test-product").clearsData)
            val result = org.junit.Assert.assertThrows(MiUnlockClient.BusinessException::class.java) {
                client.requestUnlock("test-product", "test-device-token")
            }
            assertEquals(20036, result.code)
            assertEquals(168, result.waitHours)
            assertEquals(
                listOf(
                    "/api/v2/nonce", "/api/v2/unlock/device/clear",
                    "/api/v2/nonce", "/api/v2/unlock/device/clear",
                    "/api/v2/nonce", "/api/v3/ahaUnlock"
                ),
                requests.map { it.first }
            )
            val firstClearNonce = requests[1].second
            val retryClearNonce = requests[3].second
            val unlockNonce = requests[5].second
            listOf(firstClearNonce, retryClearNonce, unlockNonce).forEach { assertNotNull(it) }
            assertNotEquals(firstClearNonce, retryClearNonce)
            assertNotEquals(firstClearNonce, unlockNonce)
            assertNotEquals(retryClearNonce, unlockNonce)
        } finally {
            server.stop(0)
        }
    }
}
