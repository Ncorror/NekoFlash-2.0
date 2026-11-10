package ru.forum.adbfastboottool

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AdbPacketDispatcherTest {
    @Test fun readerFailureReleasesConsumerWithoutWaitingForCommandTimeout() {
        val releaseSource = CountDownLatch(1)
        val consumerStarted = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val dispatcher = AdbPacketDispatcher(
            source = {
                releaseSource.await()
                AdbPacketDispatcher.ReadResult.Failed(AdbPacketDispatcher.FailureCode.USB_IN_FAILED, "IN failed")
            },
            onFailure = { _, _ -> failed.countDown() }
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            dispatcher.start()
            val result = executor.submit<AdbPacketDispatcher.Packet?> {
                consumerStarted.countDown()
                dispatcher.take(30_000)
            }
            assertTrue(consumerStarted.await(1, TimeUnit.SECONDS))
            releaseSource.countDown()
            assertTrue(failed.await(1, TimeUnit.SECONDS))
            assertNull(result.get(1, TimeUnit.SECONDS))
            assertEquals(AdbPacketDispatcher.FailureCode.USB_IN_FAILED, dispatcher.snapshot().lastFailureCode)
        } finally {
            releaseSource.countDown()
            dispatcher.stop()
            executor.shutdownNow()
        }
    }

    @Test fun quietLiveReaderHonorsConsumerTimeout() {
        val dispatcher = AdbPacketDispatcher({ AdbPacketDispatcher.ReadResult.Timeout }, { _, _ -> fail() })
        try {
            dispatcher.start()
            val started = System.nanoTime()
            assertNull(dispatcher.take(150))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 140)
            assertTrue(dispatcher.snapshot().running)
        } finally {
            dispatcher.stop()
        }
    }

    @Test fun orderlyReaderCloseStillDeliversAlreadyQueuedPacket() {
        val packet = AdbPacketDispatcher.Packet(1, 2, 3, 0, 0, byteArrayOf(7))
        var reads = 0
        val dispatcher = AdbPacketDispatcher(
            { if (reads++ == 0) AdbPacketDispatcher.ReadResult.PacketReady(packet) else AdbPacketDispatcher.ReadResult.Closed },
            { _, _ -> fail() }
        )
        try {
            dispatcher.start()
            assertSame(packet, dispatcher.take(1_000))
            assertNull(dispatcher.take(1_000))
        } finally {
            dispatcher.stop()
        }
    }
}
