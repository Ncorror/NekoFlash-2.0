package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbUsbInboundPipelineTest {
    @Test fun oneMiBIsSplitIntoExactlySixtyFourBoundedRequests() {
        val chunks = AdbUsbInboundPipeline.plan(1_048_576)
        assertEquals(64, chunks.size)
        assertEquals(AdbUsbInboundPipeline.Chunk(0, 0, 16_384), chunks.first())
        assertEquals(AdbUsbInboundPipeline.Chunk(63, 1_032_192, 16_384), chunks.last())
        assertEquals(1_048_576, chunks.sumOf { it.size })
        assertEquals(4, AdbUsbInboundPipeline.MAX_IN_FLIGHT)
    }

    @Test fun aPartialFinalChunkNeverOverlapsNextAdbFrame() {
        val chunks = AdbUsbInboundPipeline.plan(422_256)
        assertEquals(26, chunks.size)
        assertEquals(AdbUsbInboundPipeline.Chunk(25, 409_600, 12_656), chunks.last())
        assertEquals(422_256, chunks.sumOf { it.size })
    }

    @Test fun aFullPayloadCanCompleteOutOfOrder() {
        val chunks = AdbUsbInboundPipeline.plan(65_536)
        val tracker = AdbUsbInboundPipeline.Tracker(chunks)
        assertTrue(tracker.accept(2, chunks[2].size))
        assertTrue(tracker.accept(0, chunks[0].size))
        assertTrue(tracker.accept(3, chunks[3].size))
        assertFalse(tracker.complete)
        assertTrue(tracker.accept(1, chunks[1].size))
        assertTrue(tracker.complete)
        assertEquals(4, tracker.acceptedChunks)
        assertEquals(65_536, tracker.acceptedBytes)
    }

    @Test fun duplicateCompletionCannotCountTwice() {
        val chunks = AdbUsbInboundPipeline.plan(32_768)
        val tracker = AdbUsbInboundPipeline.Tracker(chunks)
        assertTrue(tracker.accept(0, 16_384))
        assertFalse(tracker.accept(0, 16_384))
        assertFalse(tracker.complete)
        assertEquals(16_384, tracker.acceptedBytes)
    }

    @Test fun shortOrOversizedUsbCompletionFailsClosed() {
        val chunks = AdbUsbInboundPipeline.plan(1_048_576)
        val tracker = AdbUsbInboundPipeline.Tracker(chunks)
        assertFalse(tracker.accept(0, 0))
        assertFalse(tracker.accept(0, -1))
        assertFalse(tracker.accept(0, 16_383))
        assertFalse(tracker.accept(0, 16_385))
        assertFalse(tracker.accept(64, 16_384))
        assertFalse(tracker.complete)
        assertEquals(0, tracker.acceptedBytes)
    }

    @Test fun emptyPayloadAndInvalidLengthsHaveExplicitBehavior() {
        val empty = AdbUsbInboundPipeline.plan(0)
        assertTrue(empty.isEmpty())
        assertTrue(AdbUsbInboundPipeline.Tracker(empty).complete)
        for (invalid in listOf(-1, 1_048_577)) {
            var rejected = false
            try {
                AdbUsbInboundPipeline.plan(invalid)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Unexpected acceptance of length=$invalid", rejected)
        }
    }
}
