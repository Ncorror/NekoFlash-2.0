package ru.forum.adbfastboottool

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One lazy execution handed to the foreground service, never to an Activity scope. */
internal class ServiceOwnedOperation(
    val id: String,
    val title: String,
    val description: String,
    val tracksProgress: Boolean,
    private val cancelTransport: () -> Unit,
    onFinished: (ServiceOwnedOperation, Throwable?) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    execute: suspend CoroutineScope.() -> Unit
) {
    private val admissionLock = Any()
    private var admitted = false
    @Volatile var startupFailure: String? = null
        private set
    @Volatile var serviceLost: Boolean = false
        private set

    val finished = CompletableDeferred<Unit>()
    val job: Job = CoroutineScope(dispatcher).launch(start = CoroutineStart.LAZY, block = execute)

    init {
        // Also runs if a lazy job is cancelled before its body ever starts.
        job.invokeOnCompletion { cause ->
            try { onFinished(this, cause) } finally { finished.complete(Unit) }
        }
    }

    fun acknowledgeForeground(): Boolean = synchronized(admissionLock) {
        if (admitted || startupFailure != null || job.isCancelled || job.isCompleted) return false
        admitted = true
        job.start()
    }

    fun rejectStartup(message: String): Boolean {
        synchronized(admissionLock) {
            if (admitted || startupFailure != null || job.isCompleted) return false
            startupFailure = message
        }
        job.cancel(CancellationException(message))
        return true
    }

    fun cancel(reason: String, serviceDestroyed: Boolean = false) {
        if (job.isCompleted) return
        if (serviceDestroyed) serviceLost = true
        // Cancellation must precede waking blocking USB code. Completion is not
        // reported until that code and the existing native drain have returned.
        job.cancel(CancellationException(reason))
        cancelTransport()
    }
}

/** Tokens prevent a late start/stop from a previous operation affecting the next. */
internal class ServiceOperationRegistry {
    private var current: ServiceOwnedOperation? = null

    @Synchronized fun register(operation: ServiceOwnedOperation): Boolean {
        if (current != null) return false
        current = operation
        return true
    }
    @Synchronized fun find(id: String?): ServiceOwnedOperation? = current?.takeIf { it.id == id }
    @Synchronized fun isEmpty(): Boolean = current == null
    @Synchronized fun remove(id: String) {
        if (current?.id == id) current = null
    }
}
