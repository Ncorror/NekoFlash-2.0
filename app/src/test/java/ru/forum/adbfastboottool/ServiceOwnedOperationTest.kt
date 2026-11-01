package ru.forum.adbfastboottool

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ServiceOwnedOperationTest {
    private fun task(id: String = "one", calls: AtomicInteger = AtomicInteger(),
        finished: AtomicInteger = AtomicInteger(), execute: () -> Unit = {}): ServiceOwnedOperation =
        ServiceOwnedOperation(id, "flash", "boot.img", true, {}, { _, _ -> finished.incrementAndGet() }) {
            calls.incrementAndGet()
            execute()
        }

    @Test fun pendingRequestCannotExecuteBeforeForegroundAck() = runBlocking {
        val calls = AtomicInteger()
        val operation = task(calls = calls)
        assertFalse(operation.job.isActive)
        assertFalse(operation.job.isCompleted)
        assertEquals(0, calls.get())
        assertTrue(operation.acknowledgeForeground())
        withTimeout(5000) { operation.finished.await() }
        assertEquals(1, calls.get())
    }

    @Test fun duplicateAckNeverReplaysTheCommand() = runBlocking {
        val calls = AtomicInteger()
        val operation = task(calls = calls)
        operation.acknowledgeForeground()
        assertFalse(operation.acknowledgeForeground())
        withTimeout(5000) { operation.finished.await() }
        assertEquals(1, calls.get())
    }

    @Test fun rejectedStartupCompletesWithoutEnteringTheBody() = runBlocking {
        val calls = AtomicInteger()
        val finished = AtomicInteger()
        val operation = task(calls = calls, finished = finished)
        assertTrue(operation.rejectStartup("startForeground failed"))
        assertFalse(operation.acknowledgeForeground())
        withTimeout(5000) { operation.finished.await() }
        assertEquals(0, calls.get())
        assertEquals(1, finished.get())
        assertEquals("startForeground failed", operation.startupFailure)
    }

    @Test fun cancellationBeforeServiceArrivalNeverExecutes() = runBlocking {
        val calls = AtomicInteger()
        val operation = task(calls = calls)
        operation.cancel("cancel before acknowledgement")
        assertFalse(operation.acknowledgeForeground())
        withTimeout(5000) { operation.finished.await() }
        assertEquals(0, calls.get())
    }

    @Test fun startupTimeoutCannotCancelAnAlreadyAdmittedOperation() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val operation = task { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            operation.acknowledgeForeground()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(operation.rejectStartup("late timeout"))
            assertFalse(operation.job.isCancelled)
        } finally { release.countDown() }
        withTimeout(5000) { operation.finished.await() }
    }

    @Test fun cancellationWaitsForBlockingNativeLikeWorkToReturn() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = AtomicInteger()
        val operation = task(finished = finished) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            operation.acknowledgeForeground()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            operation.cancel("safe cancel")
            assertTrue(operation.job.isCancelled)
            assertFalse(operation.job.isCompleted)
            assertFalse(operation.finished.isCompleted)
            assertEquals(0, finished.get())
        } finally { release.countDown() }
        withTimeout(5000) { operation.finished.await() }
        assertEquals(1, finished.get())
    }

    @Test fun destroyingUiScopeDoesNotCancelServiceExecution() = runBlocking {
        val ui = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ready = CompletableDeferred<ServiceOwnedOperation>()
        val screenJob = ui.launch {
            ready.complete(task { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
            awaitCancellation()
        }
        val operation = withTimeout(5000) { ready.await() }
        try {
            operation.acknowledgeForeground()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            ui.cancel()
            assertTrue(screenJob.isCancelled)
            assertTrue(operation.job.isActive)
        } finally { release.countDown() }
        withTimeout(5000) { operation.finished.await() }
    }

    @Test fun serviceLossIsDistinguishableFromUserCancellation() = runBlocking {
        val operation = task()
        operation.cancel("service destroyed", serviceDestroyed = true)
        withTimeout(5000) { operation.finished.await() }
        assertTrue(operation.serviceLost)
    }

    @Test fun registryRejectsOverlapAndIgnoresStaleCompletionTokens() = runBlocking {
        val registry = ServiceOperationRegistry()
        val first = task("one")
        val second = task("two")
        assertTrue(registry.register(first))
        assertFalse(registry.register(second))
        registry.remove("stale")
        assertSame(first, registry.find("one"))
        first.cancel("done")
        withTimeout(5000) { first.finished.await() }
        registry.remove("one")
        assertTrue(registry.register(second))
        registry.remove("one")
        assertSame(second, registry.find("two"))
        second.cancel("done")
        withTimeout(5000) { second.finished.await() }
        registry.remove("two")
        assertTrue(registry.isEmpty())
    }

    @Test fun freshProcessRegistryHasNoReplayableWork() {
        val registry = ServiceOperationRegistry()
        assertNull(registry.find("persisted-or-redelivered-token"))
        assertTrue(registry.isEmpty())
    }
}
