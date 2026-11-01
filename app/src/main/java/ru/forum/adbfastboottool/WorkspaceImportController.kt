package ru.forum.adbfastboottool

import android.app.Application
import android.net.Uri
import android.os.CancellationSignal
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Process-session owner, without an Activity reference or replay on rotation. */
internal class WorkspaceImportController(private val application: Application, private val log: (String) -> Unit) {
    enum class Phase { COPYING, CANCELLING, COMPLETED, CANCELLED, FAILED }
    data class State(val name: String = "", val copied: Long = 0, val expected: Long? = null,
                     val phase: Phase = Phase.COPYING, val target: String? = null,
                     val error: String? = null, val required: Long? = null, val available: Long? = null) {
        val finished: Boolean get() = phase in setOf(Phase.COMPLETED, Phase.CANCELLED, Phase.FAILED)
    }
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableLiveData<State?>(null)
    val state: LiveData<State?> = _state
    private var snapshot: State? = null
    private var control: ImportCancellation? = null
    val isBusy: Boolean get() = synchronized(lock) { control != null }

    fun start(uri: Uri, workspace: File): Boolean = synchronized(lock) {
        if (control != null) return false
        val cancellation = ImportCancellation { close ->
            // Provider close can block; never close on the UI thread or as a
            // child of the cancelled job whose read it must unblock.
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { close() })
        }
        control = cancellation
        publish(State())
        scope.launch {
            var current = State()
            try {
                val resolver = application.contentResolver
                val signal = CancellationSignal()
                cancellation.attachInputAbort(signal::cancel)
                try {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null, null, null, signal)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            current = current.copy(
                                name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else "",
                                expected = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0 } else null)
                        }
                    }
                } catch (_: Exception) {
                    // Metadata is optional; stream access may still be supported.
                    cancellation.check()
                }
                cancellation.check()
                current = current.copy(name = WorkspaceImportNaming.sanitizeImportedFileName(
                    current.name.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "imported-${System.currentTimeMillis()}" }))
                publish(current)
                log(application.getString(R.string.rev11_import_started, current.name, workspace.absolutePath))
                current.expected?.let { log(application.getString(R.string.rev11_import_expected_size, it)) }
                var lastProgressAt = 0L
                val target = WorkspaceFileImporter.importFile(workspace, application.packageName,
                    current.name, current.expected, cancellation,
                    openInput = {
                        val descriptor = resolver.openAssetFileDescriptor(uri, "r", signal)
                            ?: throw IOException("Could not open source document")
                        val stream = try { descriptor.createInputStream() }
                            catch (error: Throwable) { descriptor.close(); throw error }
                        val owned = object : FilterInputStream(stream) {
                            override fun close() { try { super.close() } finally { descriptor.close() } }
                        }
                        try { cancellation.attachInputAbort { try { signal.cancel() } finally { owned.close() } } }
                        catch (error: Throwable) { owned.close(); throw error }
                        // Poll the underlying provider FD before each read. On API 26,
                        // closing a blocked pipe read from another thread may not wake it.
                        CancellableProviderInputStream(owned, descriptor.fileDescriptor, cancellation::check)
                    },
                    onProgress = { copied ->
                        current = current.copy(copied = copied)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgressAt >= 100L) { publish(current); lastProgressAt = now }
                    },
                    availableSpace = { StatFs(it.absolutePath).availableBytes },
                    privateLockFile = File(application.filesDir, "workspace-import.lock"))
                current = current.copy(phase = Phase.COMPLETED, target = target.absolutePath)
                log("✅ " + application.getString(R.string.rev11_import_finished, target.absolutePath, "${target.length()} B"))
            } catch (error: Exception) {
                val cancelled = try { cancellation.check(); false } catch (_: CancellationException) { true }
                current = if (cancelled && error.suppressed.isEmpty()) current.copy(phase = Phase.CANCELLED)
                else current.copy(phase = Phase.FAILED, error = error.message ?: error.javaClass.simpleName,
                    required = (error as? LocalFileCopy.NotEnoughSpace)?.required,
                    available = (error as? LocalFileCopy.NotEnoughSpace)?.available)
                log(if (current.phase == Phase.CANCELLED) application.getString(R.string.rev26_import_cancelled)
                    else "❌ " + application.getString(R.string.rev11_import_failed, current.error.orEmpty()))
            } finally {
                synchronized(lock) { if (control === cancellation) { control = null; publish(current) } }
            }
        }
        true
    }
    fun cancel() = synchronized(lock) {
        if (control?.cancel() == true) snapshot?.let { publish(it.copy(phase = Phase.CANCELLING)) }
    }
    fun dismissResult() = synchronized(lock) { if (control == null) { snapshot = null; _state.postValue(null) } }
    fun close() { cancel(); scope.cancel() }
    private fun publish(next: State) = synchronized(lock) {
        val value = if (snapshot?.phase == Phase.CANCELLING && next.phase == Phase.COPYING) next.copy(phase = Phase.CANCELLING) else next
        snapshot = value
        _state.postValue(value)
    }
}
