package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbUsbInboundReadPolicyTest {
    @Test fun android16RequestsOneMiBInSingleUsbTransfer() {
        val calls = mutableListOf<Triple<Int, Int, Int>>()
        val result = AdbUsbInboundReadPolicy.readExact(1_048_576, 36) { offset, bytes, timeout ->
            calls += Triple(offset, bytes, timeout)
            bytes
        }
        assertEquals(listOf(Triple(0, 1_048_576, 15_000)), calls)
        assertEquals(AdbUsbInboundReadPolicy.Result.Complete(1_048_576, 1), result)
    }

    @Test fun android9AlsoUsesFullSizeWhileAndroid8StaysAt16KiB() {
        val modern = mutableListOf<Int>()
        val modernResult = AdbUsbInboundReadPolicy.readExact(65_536, 28) { _, requested, _ ->
            modern += requested
            requested
        }
        assertEquals(listOf(65_536), modern)
        assertEquals(AdbUsbInboundReadPolicy.Result.Complete(65_536, 1), modernResult)

        val legacy = mutableListOf<Triple<Int, Int, Int>>()
        val legacyResult = AdbUsbInboundReadPolicy.readExact(65_536, 27) { offset, requested, timeout ->
            legacy += Triple(offset, requested, timeout)
            requested
        }
        assertEquals(
            listOf(
                Triple(0, 16_384, 5_000),
                Triple(16_384, 16_384, 5_000),
                Triple(32_768, 16_384, 5_000),
                Triple(49_152, 16_384, 5_000)
            ),
            legacy
        )
        assertEquals(AdbUsbInboundReadPolicy.Result.Complete(65_536, 4), legacyResult)
    }

    @Test fun shortPositiveReadContinuesAtExactOffsetWithOnlyRemainder() {
        val calls = mutableListOf<Triple<Int, Int, Int>>()
        val result = AdbUsbInboundReadPolicy.readExact(1_048_576, 36) { offset, requested, timeout ->
            calls += Triple(offset, requested, timeout)
            if (offset == 0) 1_015_808 else requested
        }
        assertEquals(
            listOf(
                Triple(0, 1_048_576, 15_000),
                Triple(1_015_808, 32_768, 15_000)
            ),
            calls
        )
        assertEquals(AdbUsbInboundReadPolicy.Result.Complete(1_048_576, 2), result)
    }

    @Test fun negativeReadIsTerminalAndNeverAutomaticallyRetried() {
        var calls = 0
        val result = AdbUsbInboundReadPolicy.readExact(1_048_576, 36) { offset, _, _ ->
            calls++
            if (offset == 0) 1_015_808 else -1
        }
        assertEquals(2, calls)
        assertEquals(
            AdbUsbInboundReadPolicy.Result.Failed(
                bytesRead = 1_015_808,
                requestedBytes = 32_768,
                transferResult = -1,
                requests = 2
            ),
            result
        )
    }

    @Test fun impossibleUsbResultOrZeroIsNeverAccepted() {
        val oversized = AdbUsbInboundReadPolicy.readExact(1024, 36) { _, requested, _ ->
            requested + 1
        }
        assertEquals(AdbUsbInboundReadPolicy.Result.Failed(0, 1024, 1025, 1), oversized)
        val noData = AdbUsbInboundReadPolicy.readExact(1024, 26) { _, _, _ -> 0 }
        assertEquals(AdbUsbInboundReadPolicy.Result.Failed(0, 1024, 0, 1), noData)
    }

    @Test fun emptyPayloadNeedsNoUsbReadAndInvalidSizesAreRejected() {
        var calls = 0
        val empty = AdbUsbInboundReadPolicy.readExact(0, 36) { _, _, _ ->
            calls++
            1
        }
        assertEquals(0, calls)
        assertEquals(AdbUsbInboundReadPolicy.Result.Complete(0, 0), empty)
        for (size in listOf(-1, 1_048_577)) {
            var rejected = false
            try {
                AdbUsbInboundReadPolicy.readExact(size, 36) { _, n, _ -> n }
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Size $size must be rejected", rejected)
        }
    }
}
