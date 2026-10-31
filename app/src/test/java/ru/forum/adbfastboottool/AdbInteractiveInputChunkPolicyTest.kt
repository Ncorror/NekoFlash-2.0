package ru.forum.adbfastboottool

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbInteractiveInputChunkPolicyTest {
    private val policy = AdbInteractiveInputChunkPolicy

    private fun reconstruct(payload: ByteArray, maxdata: Int, framed: Int): ByteArray {
        val output = ByteArrayOutputStream()
        policy.fragments(payload.size, maxdata, framed).forEach { slice ->
            val count = slice.last - slice.first + 1
            assertTrue(count > 0)
            assertTrue(count + framed <= maxdata)
            output.write(payload, slice.first, count)
        }
        return output.toByteArray()
    }

    @Test fun oldFourKilobyteDevicesNeverReceiveOversizeV2Frames() {
        val input = ("Привет 🐈\n".repeat(10000)).toByteArray(Charsets.UTF_8)
        val maxdata = policy.maxWirePayload(4096, 1_048_576)
        assertEquals(4096, maxdata)
        assertArrayEquals(input, reconstruct(input, maxdata, framed = 5))
        val first = policy.fragments(input.size, maxdata, 5).first()
        assertEquals(4091, first.last - first.first + 1)
    }

    @Test fun legacyAndModernDevicesPreserveEveryByteAcrossFragments() {
        val input = ByteArray(2_100_000) { ((it * 37) and 0xff).toByte() }
        assertArrayEquals(input, reconstruct(input, 4096, framed = 0))
        assertArrayEquals(input, reconstruct(input, 262144, framed = 5))
        assertArrayEquals(input, reconstruct(input, 1048576, framed = 5))
    }

    @Test fun peerAdvertisedMaxdataWinsAndNoTotalInputCapIsIntroduced() {
        assertEquals(4096, policy.maxWirePayload(0, 1_048_576))
        assertEquals(512, policy.maxWirePayload(512, 1_048_576))
        assertEquals(262144, policy.maxWirePayload(262144, 1_048_576))
        assertEquals(1048576, policy.maxWirePayload(2_000_000, 1_048_576))
        assertEquals(0, policy.fragments(0, 4096, 5).count())
        assertFalse(policy.fragments(100_000, 4096, 5).toList().isEmpty())
    }

    @Test fun tooSmallOrInvalidFramingIsRejectedRatherThanOverflowing() {
        try {
            policy.fragments(128, 5, 5).toList()
            throw AssertionError("Must reject maxdata smaller than framing")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("maxdata"))
        }
        try {
            policy.fragments(128, 4096, -1).toList()
            throw AssertionError("Must reject negative framing")
        } catch (_: IllegalArgumentException) {
        }
    }
}
