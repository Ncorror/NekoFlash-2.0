package ru.forum.adbfastboottool

import java.util.concurrent.CancellationException

/** Cancellation and publication share one ordering point. A published file wins a late cancel. */
internal class ImportCancellation(private val closeOffThread: (() -> Unit) -> Unit = { it() }) {
    private val lock = Any()
    @Volatile private var requested = false
    private var committed = false
    private var abortInput: (() -> Unit)? = null
    fun check() { if (requested) throw CancellationException("File import cancelled") }
    fun attachInputAbort(action: () -> Unit) {
        val abortNow = synchronized(lock) { abortInput = action; requested }
        if (abortNow) closeOffThread { runCatching(action) }
        check()
    }
    fun detachInput() { synchronized(lock) { abortInput = null } }
    fun cancel(): Boolean {
        val abort = synchronized(lock) {
            if (committed || requested) return false
            requested = true
            abortInput
        }
        if (abort != null) closeOffThread { runCatching(abort) }
        return true
    }
    fun <T> commit(publish: () -> T): T = synchronized(lock) {
        check()
        publish().also { committed = true; abortInput = null }
    }
}
