package ru.forum.adbfastboottool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AdbPacketDispatcherWakeTest {
    @Test fun aTransportFailureWakesThirtySecondPullWaitPromptly() {
        val failed = CountDownLatch(1)
        val dispatcher = AdbPacketDispatcher(
            source = { AdbPacketDispatcher.ReadResult.Failed(
                AdbPacketDispatcher.FailureCode.USB_IN_FAILED, "synthetic 16KiB USB IN error"
            ) },
            onFailure = { _, _ -> failed.countDown() }
        )
        assertTrue(dispatcher.start())
        assertTrue("reader should fail", failed.await(2, TimeUnit.SECONDS))
        val startNs = System.nanoTime()
        assertNull(dispatcher.take(30_000))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        assertTrue("terminal failure still blocked for ${elapsedMs}ms", elapsedMs < 1000)
        assertFalse(dispatcher.snapshot().running)
        assertTrue(dispatcher.snapshot().readFailures > 0)
        dispatcher.stop()
    }

    @Test fun anExplicitStopAlsoWakesQueuedRead() {
        val dispatcher = AdbPacketDispatcher(
            source = { AdbPacketDispatcher.ReadResult.Timeout },
            onFailure = { _, _ -> }
        )
        assertTrue(dispatcher.start())
        dispatcher.stop()
        val startNs = System.nanoTime()
        assertNull(dispatcher.take(30_000))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        assertTrue("closed dispatcher still blocked for ${elapsedMs}ms", elapsedMs < 1000)
    }
}
