package ru.forum.adbfastboottool

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
