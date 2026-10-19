package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootDataReplyPolicyTest {
    @Test fun acceptsExactHexReply() {
        assertEquals(
            FastbootDataReplyPolicy.Verdict.Accepted(0x10000000L),
            FastbootDataReplyPolicy.validate("10000000", 0x10000000L)
        )
    }

    @Test fun acceptsUppercaseHexAndWireMaximum() {
        assertEquals(
            FastbootDataReplyPolicy.Verdict.Accepted(0xFFFF_FFFFL),
            FastbootDataReplyPolicy.validate("FFFFFFFF", 0xFFFF_FFFFL)
        )
    }

    @Test fun rejectsSmallerDataAckBeforeAnyUsbPayload() {
        assertEquals(
            FastbootDataReplyPolicy.Verdict.Mismatch(4096L, 2048L),
            FastbootDataReplyPolicy.validate("00000800", 4096L)
        )
    }

    @Test fun rejectsLargerDataAckBeforeAnyUsbPayload() {
        assertEquals(
            FastbootDataReplyPolicy.Verdict.Mismatch(1024L, 4096L),
            FastbootDataReplyPolicy.validate("00001000", 1024L)
        )
    }

    @Test fun rejectsMalformedAndTruncatedReplies() {
        listOf("", "0000010", "000000001", "0x000100", "gg000000", "0000 100", "0000\n100")
            .forEach { reply ->
                assertTrue(FastbootDataReplyPolicy.validate(reply, 256L) is FastbootDataReplyPolicy.Verdict.Invalid)
            }
    }

    @Test fun rejectsZeroSizedDataAck() {
        assertEquals(
            FastbootDataReplyPolicy.Verdict.Mismatch(1L, 0L),
            FastbootDataReplyPolicy.validate("00000000", 1L)
        )
    }
}
