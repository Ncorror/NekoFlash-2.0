package ru.forum.adbfastboottool

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Lifecycle-владелец USB-сессии и пользовательских операций NekoFlash.
 *
 * ViewModel сериализует подключение ADB/Fastboot, хранит наблюдаемое состояние экранов
 * и публикует прогресс. Mutation-команды передаются напрямую реальному protocol-слою;
 * ViewModel не вводит отдельные host-side разрешения поверх bootloader/ADB.
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    enum class ConnectionState { NONE, CONNECTING, FASTBOOT, ADB, ERROR }

    enum class OperationStepStatus { PENDING, RUNNING, OK, FAILED, SKIPPED, INFO }

    enum class OperationOutcomeKind { SUCCESS, FAILED, CANCELLED, VERIFY_PENDING, INTERRUPTED }

    sealed class OperationOutcome {
        object Success : OperationOutcome()
        data class Failed(val message: String) : OperationOutcome()
        data class Cancelled(val message: String) : OperationOutcome()
        data class VerifyPending(val message: String) : OperationOutcome()
        data class Interrupted(val message: String) : OperationOutcome()
    }

    private class OperationAbort(val outcome: OperationOutcome) :
        RuntimeException(null, null, false, false)

    private class OperationContext {
        fun failOperation(message: String): Nothing =
            throw OperationAbort(OperationOutcome.Failed(message))

        fun verificationPending(message: String): Nothing =
            throw OperationAbort(OperationOutcome.VerifyPending(message))
    }

    data class OperationStep(
        val index: Int,
        val total: Int,
        val title: String,
        val subtitle: String? = null,
        val status: OperationStepStatus = OperationStepStatus.PENDING
    )

    private val recoveryStore = OperationRecoveryStore(File(application.filesDir, "execution-state"))
    private val checkpointLock = Any()
    @Volatile private var persistedOperation: PersistedOperation? = null
    private var lastCheckpointAtMs = 0L
    @Volatile private var activeServiceOperation: ServiceOwnedOperation? = null
    @Volatile private var operationProgressSnapshot: OperationProgress? = null

    private val _connectionState   = MutableLiveData(ConnectionState.NONE)
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _operationActive   = MutableLiveData(false)
    val operationActive: LiveData<Boolean> = _operationActive

    /** Share the launch lock so workspace cleanup cannot race a new USB job. */
    fun runWorkspaceMaintenanceIfIdle(action: () -> Unit): Boolean = synchronized(operationLaunchLock) {
        if (operationJob != null || connectionJob?.isCompleted == false ||
            NativeUsbfsBackend.hasActiveTransfer || NativeUsbfsBackend.backendState().nativeTransferActive
        ) return@synchronized false
        action()
        true
    }

    private val _operationSteps = MutableLiveData<List<OperationStep>>(emptyList())
    val operationSteps: LiveData<List<OperationStep>> = _operationSteps

    /**
     * Прогресс активной операции записи (для полноэкранного диалога прошивки).
     * null — операция без явного прогресса или не идёт.
     */
    data class OperationProgress(
        val title: String,        // что прошивается, напр. "flash recovery_a"
        val percent: Int,         // 0..100, -1 = неопределённый (busy)
        val detail: String,       // linesа скорости/ETA или статус
        val finished: Boolean = false,  // operation завершена (показать результат)
        val success: Boolean = false,   // успех (для обратной совместимости UI)
        val outcome: OperationOutcomeKind? = null
    )

    private val _operationProgress = MutableLiveData<OperationProgress?>(null)
    val operationProgress: LiveData<OperationProgress?> = _operationProgress

    fun postOperationProgress(progress: OperationProgress?) {
        operationProgressSnapshot = progress
        _operationProgress.postValue(progress)
        val task = activeServiceOperation
        if (task == null) {
            // A later verified Recovery verdict can finalize the persisted pending result.
            if (progress?.finished == true && progress.outcome != null) synchronized(checkpointLock) {
                val current = persistedOperation
                if (current?.phase == PersistedOperationPhase.VERIFY_PENDING && current.title == progress.title) {
                    val updated = current.copy(detail = progress.detail, percent = progress.percent.coerceIn(-1, 100),
                        phase = PersistedOperationPhase.valueOf(progress.outcome.name))
                    persistedOperation = updated
                    if (updated != current) runCatching { recoveryStore.writeOperation(updated) }
                        .onFailure { logFileOnly("Recovery verdict checkpoint failed: ${it.message}") }
                }
            }
            return
        }
        if (progress == null || !task.tracksProgress) return
        synchronized(checkpointLock) {
            val current = persistedOperation ?: return
            if (current.id != task.id || current.phase != PersistedOperationPhase.RUNNING) return
            val updated = current.copy(detail = progress.detail, percent = progress.percent.coerceIn(-1, 100))
            persistedOperation = updated
            val now = SystemClock.elapsedRealtime()
            if (now - lastCheckpointAtMs >= 1000L) {
                lastCheckpointAtMs = now
                runCatching { recoveryStore.writeOperation(updated) }
                    .onFailure { logFileOnly("Operation progress checkpoint failed: ${it.message}") }
            }
        }
    }

    private val _connectionInfo    = MutableLiveData<String?>(null)
    val connectionInfo: LiveData<String?> = _connectionInfo

    private val _fastbootDiagnostics = MutableLiveData<FastbootProtocol.DeviceDiagnostics?>(null)
    val fastbootDiagnostics: LiveData<FastbootProtocol.DeviceDiagnostics?> = _fastbootDiagnostics

    private val _fastbootPartitionInventory = MutableLiveData<FastbootPartitionInventory.Snapshot?>(null)
    val fastbootPartitionInventory: LiveData<FastbootPartitionInventory.Snapshot?> = _fastbootPartitionInventory

    private val _adbPeerMode = MutableLiveData<AdbProtocol.PeerMode?>(null)
    val adbPeerMode: LiveData<AdbProtocol.PeerMode?> = _adbPeerMode

    private val _transportSessionId = MutableLiveData<String?>(null)
    val transportSessionId: LiveData<String?> = _transportSessionId

    private val flashDraftLock = Any()
    private var flashDraftSnapshot: FlashOperationDraft =
        runCatching { recoveryStore.readDraft() }.getOrNull() ?: FlashOperationDraft()
    private val _flashOperationDraft = MutableLiveData(flashDraftSnapshot)
    val flashOperationDraft: LiveData<FlashOperationDraft> = _flashOperationDraft

    /** Transient execution object. Only the lightweight draft is persisted. */
    data class FlashQueueItem(val partition: String, val file: File, val expectedSizeBytes: Long = file.length())

    private data class PendingUnlockVerification(
        val product: String,
        val serial: String?,
        val expectedUnlocked: Boolean,
        val operationLabel: String,
        val createdAtMs: Long
    )

    private data class PendingSideloadVerification(
        val attemptId: String,
        val packageName: String,
        val packageSize: Long,
        val serial: String?,
        val createdAtMs: Long,
        val boundary: RecoveryLogBoundary?,
        val transferFinished: Boolean
    )

    private data class PreparedRecoveryLog(
        val serial: String,
        val boundary: RecoveryLogBoundary,
        val createdAtMs: Long
    )

    @Volatile private var preparedRecoveryLog: PreparedRecoveryLog? = null
    @Volatile private var sideloadGuiAttemptId: String? = null

    private fun text(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private val initialLines = listOf(
        text(R.string.system_terminal_ready),
        text(R.string.security_full_terminal_active)
    )

    private val _logLines = MutableLiveData(initialLines)
    val logLines: LiveData<List<String>> = _logLines

    private val lines    = initialLines.toMutableList()
    private val logLock  = Any()
    private val operationStepLock = Any()
    private var operationStepSnapshot: List<OperationStep> = emptyList()
    private var logFile: File? = null
    private var traceLogFile: File? = null
    private var logStore: DiagnosticLogStore? = null
    private var logStorageFailureNotified = false
    private var sessionSummaryFile: File? = null
    private var logFileConfigured = false
    private var configuredWorkspacePath: String? = null
    private var lastCompactMessage: String? = null
    private var lastCompactDeclaredLevel: DiagnosticLogPolicy.Level? = null
    private var lastCompactMessageAtMs: Long = 0L
    private var suppressedDuplicateCount: Long = 0L
    private var lastSummaryPersistAtMs: Long = 0L
    private var workspaceRoot: File? = null
    private var lastUsbSessionSnapshot: UsbSessionSnapshot? = null
    private var connectedUsbManager: UsbManager? = null
    private var activeTransportSessionId: String? = null
    private val transportSessionSequence = AtomicLong(0L)
    private val adbKeyDir: File = File(application.filesDir, "adbkeys")

    @Volatile
    var fastbootProtocol: FastbootProtocol? = null
        private set
    @Volatile
    var adbProtocol: AdbProtocol? = null
        private set

    @Volatile
    private var connectionJob: Job? = null
    @Volatile
    private var operationJob: Job? = null
    private val operationLaunchLock = Any()
    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val transportTransitionMutex = Mutex()
    private val transportRestartRequired = AtomicBoolean(false)
    private val viewModelCleared = AtomicBoolean(false)
    @Volatile
    private var connectedUsbTarget: UsbDeviceInspector.Candidate? = null
    @Volatile
    private var pendingUsbTargetKey: String? = null
    @Volatile
    private var connectedDeviceInfo: String? = null
    private var debugLoggingEnabled: Boolean = false
    private var operationWakeLock: PowerManager.WakeLock? = null

    private val operationGeneration = AtomicLong(0L)
    private val connectionGeneration = AtomicLong(0L)
    private val processSessionId: String = UUID.randomUUID().toString()
    private val diagnosticSessionTracker = DiagnosticSessionTracker(processSessionId, buildId = BuildConfig.BUILD_ID)


    init {
        DiagnosticCrashMarker.install(File(application.filesDir, "diagnostics"), BuildConfig.BUILD_ID)
        restoreOperationState()
    }

    internal fun lastPersistedOperation(id: String): PersistedOperation? = persistedOperation?.takeIf { it.id == id }

    private fun restoreOperationState() {
        try {
            val previous = recoveryStore.readOperation() ?: return
            val recovered = previous.afterProcessRestart().let {
                if (it.phase == PersistedOperationPhase.INTERRUPTED && previous.phase != PersistedOperationPhase.INTERRUPTED) {
                    it.copy(detail = text(R.string.rev20_interrupted_detail, previous.detail))
                } else it
            }
            persistedOperation = recovered
            if (recovered != previous) recoveryStore.writeOperation(recovered)
            val outcome = OperationOutcomeKind.valueOf(recovered.phase.name)
            postOperationProgress(OperationProgress(recovered.title, recovered.percent, recovered.detail,
                finished = true, success = outcome == OperationOutcomeKind.SUCCESS, outcome = outcome))
            if (outcome == OperationOutcomeKind.INTERRUPTED) log(recovered.detail)
        } catch (error: Exception) {
            val message = text(R.string.rev20_state_unreadable)
            log(message)
            logFileOnly("Operation recovery: ${error.message}")
            postOperationProgress(OperationProgress(text(R.string.rev20_operation_interrupted), -1, message,
                finished = true, outcome = OperationOutcomeKind.INTERRUPTED))
        }
    }

    // ─── ЛОГИРОВАНИЕ ─────────────────────────────────────────────────────────

    fun configureLogDirectory(workspacePath: File) {
        workspaceRoot = workspacePath

        val workspaceKey = runCatching { workspacePath.canonicalPath }.getOrElse { workspacePath.absolutePath }
        if (logFileConfigured && configuredWorkspacePath == workspaceKey && logFile != null) {
            // Activity recreation must not start a second log file or replay the old terminal history.
            return
        }

        val logsDir = File(workspacePath, "logs")
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            log("⚠️ " + text(R.string.rev11_log_folder_failed, logsDir.absolutePath))
            return
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val seedInitialLines = !logFileConfigured
        val store = try {
            DiagnosticLogStore(logsDir, stamp)
        } catch (e: Exception) {
            log("⚠️ " + text(R.string.rev11_log_store_failed, e.message ?: e.javaClass.simpleName))
            return
        }
        logStore = store
        configuredWorkspacePath = workspaceKey
        logFileConfigured = true
        appendRawToLogFile(
            "# NekoFlash compact log\n# Created: $stamp\n" +
                "# Session: $processSessionId\n# Build: ${BuildConfig.BUILD_ID}\n# Raw protocol traffic is stored in trace-*.txt\n\n"
        )
        appendRawToTraceFile(
            "# NekoFlash protocol trace\n# Created: $stamp\n" +
                "# Session: $processSessionId\n# Build: ${BuildConfig.BUILD_ID}\n# This file may contain high-volume USB/Fastboot timing details.\n\n"
        )
        if (seedInitialLines) {
            synchronized(logLock) { lines.forEach { appendRawToLogFile(formatLogLine(it)) } }
        }
        val createdLog = store.currentCompactFile()
        logFile = createdLog
        traceLogFile = store.currentTraceFile()
        persistSessionSummary()
        log("Log file: /sdcard/Download/NekoFlash/logs/${createdLog?.name ?: "log-$stamp.txt"}")
        log("ℹ️ " + text(R.string.rev11_log_trace_location_info))
    }

    fun log(message: String) {
        logInternal(message, declaredLevel = null)
    }

    fun log(level: DiagnosticLogPolicy.Level, message: String) {
        logInternal(message, declaredLevel = level)
    }

    private fun logInternal(message: String, declaredLevel: DiagnosticLogPolicy.Level?) {
        synchronized(logLock) {
            val now = System.currentTimeMillis()
            val duplicate = message == lastCompactMessage &&
                declaredLevel == lastCompactDeclaredLevel &&
                now - lastCompactMessageAtMs <= DiagnosticLogPolicy.duplicateWindowMs()
            if (duplicate) {
                suppressedDuplicateCount += 1L
                diagnosticSessionTracker.recordDuplicateSuppressed()
                lastCompactMessageAtMs = now
                return
            }
            flushSuppressedDuplicatesLocked()
            if (message.trim().startsWith("💡")) {
                lines.removeAll { it.trim().startsWith("💡") }
            }
            appendCompactMessageLocked(message, declaredLevel)
            lastCompactMessage = message
            lastCompactDeclaredLevel = declaredLevel
            lastCompactMessageAtMs = now
        }
    }

    fun clearLog() {
        synchronized(logLock) {
            flushSuppressedDuplicatesLocked()
            lines.clear()
            lastCompactMessage = null
            lastCompactDeclaredLevel = null
            suppressedDuplicateCount = 0L
        }
        log(text(R.string.log_cleared))
    }

    /**
     * Raw protocol/timing trace, separated from the compact user log.
     * Trace segments are bounded and rotated independently.
     */
    fun logFileOnly(message: String) {
        synchronized(logLock) {
            diagnosticSessionTracker.recordTrace()
            appendRawToTraceFile(formatLogLine(message))
        }
    }

    private fun appendCompactMessageLocked(
        message: String,
        declaredLevel: DiagnosticLogPolicy.Level? = null
    ) {
        lines.add(message)
        while (lines.size > 600) lines.removeAt(0)
        _logLines.postValue(lines.toList())
        val classification = DiagnosticLogPolicy.classify(message, declaredLevel)
        diagnosticSessionTracker.recordCompact(message, classification)
        appendRawToLogFile(formatLogLine(message))
        val now = System.currentTimeMillis()
        if (classification.level == DiagnosticLogPolicy.Level.ERROR ||
            now - lastSummaryPersistAtMs >= 30_000L
        ) {
            lastSummaryPersistAtMs = now
            persistSessionSummary()
        }
    }

    private fun flushSuppressedDuplicatesLocked() {
        if (suppressedDuplicateCount <= 0L) return
        val repeated = "↻ Previous line repeated $suppressedDuplicateCount time(s); duplicates collapsed."
        suppressedDuplicateCount = 0L
        appendCompactMessageLocked(repeated, DiagnosticLogPolicy.Level.INFO)
    }

    private fun appendRawToLogFile(text: String) {
        try {
            logStore?.appendCompact(text)
            logFile = logStore?.currentCompactFile() ?: logFile
        } catch (error: Exception) {
            android.util.Log.w("NekoFlash", "Unable to append compact diagnostic log", error)
            notifyLogStorageFailure(error)
        }
    }

    private fun appendRawToTraceFile(text: String) {
        try {
            logStore?.appendTrace(text)
            traceLogFile = logStore?.currentTraceFile() ?: traceLogFile
        } catch (error: Exception) {
            android.util.Log.w("NekoFlash", "Unable to append protocol trace", error)
            notifyLogStorageFailure(error)
        }
    }

    /** Show a real UI warning once; never claim diagnostic persistence succeeded. */
    private fun notifyLogStorageFailure(error: Exception) {
        if (logStorageFailureNotified) return
        logStorageFailureNotified = true
        lines.add("❌ Diagnostic storage error: ${error.javaClass.simpleName}. Check free space; saved logs may be incomplete.")
        _logLines.postValue(lines.toList())
    }

    private fun persistSessionSummary(): File? = try {
        val file = logStore?.writeSessionSummary(
            DiagnosticSessionTracker.toJson(diagnosticSessionTracker.snapshot())
        )
        sessionSummaryFile = file
        file
    } catch (error: Exception) {
        android.util.Log.w("NekoFlash", "Unable to persist diagnostic session summary", error)
        sessionSummaryFile
    }

    fun flushDiagnostics(reason: String, terminal: Boolean = false) {
        synchronized(logLock) {
            flushSuppressedDuplicatesLocked()
            if (terminal) diagnosticSessionTracker.recordTermination(reason)
            appendRawToTraceFile(
                formatLogLine(
                    "[session-flush] reason=$reason terminal=$terminal " +
                        "transportSession=${activeTransportSessionId ?: "none"}"
                )
            )
            persistSessionSummary()
        }
    }

    private fun formatLogLine(message: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "[$stamp] $message\n"
    }

    // ─── ПОДКЛЮЧЕНИЕ ─────────────────────────────────────────────────────────

    private fun createTransportSessionId(candidate: UsbDeviceInspector.Candidate): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val sequence = transportSessionSequence.incrementAndGet().toString().padStart(3, '0')
        val vid = candidate.device.vendorId.toString(16).uppercase(Locale.US).padStart(4, '0')
        val pid = candidate.device.productId.toString(16).uppercase(Locale.US).padStart(4, '0')
        return "${stamp}_${candidate.mode.name}_${vid}-${pid}_$sequence"
    }

    private fun captureUsbSession(candidate: UsbDeviceInspector.Candidate, usbManager: UsbManager) {
        connectedUsbManager = usbManager
        val sessionId = createTransportSessionId(candidate)
        activeTransportSessionId = sessionId
        _transportSessionId.postValue(sessionId)
        val snapshot = UsbSessionSnapshot.capture(sessionId, candidate)
        lastUsbSessionSnapshot = snapshot
        diagnosticSessionTracker.recordTransportSession(sessionId, candidate.mode.name, connectedDeviceInfo)
        persistSessionSummary()
        log("=== USB SESSION: $sessionId ===")
        log("USB: ${candidate.displaySubtitle()}")
        logFileOnly(snapshot.toText())
        val workspace = workspaceRoot
        if (workspace != null) {
            runCatching {
                val dir = File(workspace, "reports/usb-sessions")
                if (!dir.exists()) dir.mkdirs()
                File(dir, "usb-session-$sessionId.txt").writeText(snapshot.toText(), Charsets.UTF_8)
                File(dir, "usb-session-$sessionId.json").writeText(snapshot.toJson(), Charsets.UTF_8)
            }.onFailure { error ->
                log("⚠️ USB session snapshot was not saved: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun connectDevice(
        usbManager: UsbManager,
        candidate: UsbDeviceInspector.Candidate,
        automatic: Boolean = false
    ) {
        if (transportRestartRequired.get()) {
            log("⛔ USB transport is locked after unconfirmed cleanup. Fully restart NekoFlash before a new connection.")
            _connectionState.postValue(ConnectionState.ERROR)
            return
        }

        val current = connectedUsbTarget
        val state = _connectionState.value ?: ConnectionState.NONE
        if ((current?.stableKey == candidate.stableKey || pendingUsbTargetKey == candidate.stableKey) &&
            state in setOf(ConnectionState.CONNECTING, ConnectionState.ADB, ConnectionState.FASTBOOT)
        ) {
            logFileOnly("Duplicate USB connect ignored: ${candidate.stableKey} state=$state")
            return
        }

        // Publish the transition Job under the same lock used by startOperation.
        // CoroutineStart.LAZY removes the window where a new USB operation could
        // start after a transition was requested but before connectionJob became visible.
        synchronized(operationLaunchLock) {
            if (viewModelCleared.get()) return
            val generation = connectionGeneration.incrementAndGet()
            pendingUsbTargetKey = candidate.stableKey
            _fastbootDiagnostics.postValue(null)
            _fastbootPartitionInventory.postValue(null)
            _adbPeerMode.postValue(null)
            _connectionState.postValue(ConnectionState.CONNECTING)

            val transitionJob = transportScope.launch(start = CoroutineStart.LAZY) {
                transportTransitionMutex.withLock {
                    if (viewModelCleared.get() || generation != connectionGeneration.get()) {
                        if (pendingUsbTargetKey == candidate.stableKey) pendingUsbTargetKey = null
                        return@withLock
                    }
                    if (!shutdownCurrentTransportsSafely("new USB generation=$generation")) {
                        if (pendingUsbTargetKey == candidate.stableKey) pendingUsbTargetKey = null
                        _connectionState.postValue(ConnectionState.ERROR)
                        return@withLock
                    }
                    if (viewModelCleared.get() || generation != connectionGeneration.get()) {
                        if (pendingUsbTargetKey == candidate.stableKey) pendingUsbTargetKey = null
                        return@withLock
                    }

                    pendingUsbTargetKey = null
                    connectedUsbTarget = candidate
                    connectedDeviceInfo = buildDeviceInfo(candidate)
                    captureUsbSession(candidate, usbManager)
                    _connectionInfo.postValue(connectedDeviceInfo)
                    _fastbootDiagnostics.postValue(null)
                    _fastbootPartitionInventory.postValue(null)
                    _adbPeerMode.postValue(null)
                    _connectionState.postValue(ConnectionState.CONNECTING)
                    connectCandidateLocked(usbManager, candidate, generation)
                }
            }
            connectionJob = transitionJob
            transitionJob.start()
        }
    }

    private suspend fun connectCandidateLocked(
        usbManager: UsbManager,
        candidate: UsbDeviceInspector.Candidate,
        generation: Long
    ) {
        val device = candidate.device

        if (candidate.mode == UsbDeviceInspector.Mode.FASTBOOT) {
            val proto = FastbootProtocol(
                usbManager = usbManager,
                device = device,
                onLog = { msg -> log(msg) },
                onLogVerbose = { msg -> logFileOnly(msg) },
                onProgress = { percent, detail ->
                    val currentProgress = operationProgressSnapshot
                    postOperationProgress(
                        OperationProgress(
                            title = currentProgress?.title ?: text(R.string.flash_progress_writing),
                            percent = percent,
                            detail = detail
                        )
                    )
                },
                preferredInterfaceIndex = candidate.interfaceIndex
            )
            proto.debugLogging = debugLoggingEnabled
            var published = false
            try {
                val connectStartedNs = System.nanoTime()
                if (!proto.connect()) {
                    diagnosticSessionTracker.recordMilestone("fastboot.connect.failed", (System.nanoTime() - connectStartedNs) / 1_000_000L)
                    if (generation == connectionGeneration.get()) _connectionState.postValue(ConnectionState.ERROR)
                    return
                }
                diagnosticSessionTracker.recordMilestone("fastboot.connect", (System.nanoTime() - connectStartedNs) / 1_000_000L)
                if (viewModelCleared.get() || generation != connectionGeneration.get()) return

                val qualifiedProduct = proto.qualifyConnection()
                if (qualifiedProduct == null) {
                    if (generation == connectionGeneration.get()) _connectionState.postValue(ConnectionState.ERROR)
                    log(DiagnosticLogPolicy.Level.ERROR, "ERROR: Fastboot handshake was not confirmed. Reconnection is allowed.")
                    return
                }
                if (viewModelCleared.get() || generation != connectionGeneration.get()) return

                val diagnostics = proto.refreshDiagnostics(force = true, knownProduct = qualifiedProduct)
                if (viewModelCleared.get() || generation != connectionGeneration.get()) return
                if (proto.isSessionBroken) {
                    _connectionState.postValue(ConnectionState.ERROR)
                    log(DiagnosticLogPolicy.Level.ERROR, "ERROR: USB interface opened, but a valid Fastboot exchange was not confirmed")
                    return
                }

                fastbootProtocol = proto
                published = true
                _adbPeerMode.postValue(null)
                _connectionState.postValue(ConnectionState.FASTBOOT)
                diagnosticSessionTracker.recordConnection("FASTBOOT", connectedDeviceInfo)
                persistSessionSummary()
                logConnectionStatus()
                _fastbootDiagnostics.postValue(diagnostics)
                verifyPendingUnlockIfReady(diagnostics)
            } finally {
                if (!published || generation != connectionGeneration.get() || viewModelCleared.get()) {
                    if (fastbootProtocol === proto) fastbootProtocol = null
                    proto.disconnect()
                }
            }
        } else {
            val proto = AdbProtocol(
                usbManager = usbManager,
                device = device,
                keyDirectory = adbKeyDir,
                onLog = { msg -> log(msg) },
                onProgress = { percent, detail ->
                    val currentProgress = operationProgressSnapshot
                    postOperationProgress(
                        OperationProgress(
                            title = currentProgress?.title ?: text(R.string.flash_progress_writing),
                            percent = percent,
                            detail = detail
                        )
                    )
                },
                preferredInterfaceIndex = candidate.interfaceIndex
            )
            proto.onTransportFailure = { code, message ->
                if (!viewModelCleared.get() && generation == connectionGeneration.get()) {
                    diagnosticSessionTracker.recordTermination("ADB_READER_${code.name}")
                    diagnosticSessionTracker.recordMilestone("adb.reader.failure", 0L)
                    persistSessionSummary()
                    _connectionState.postValue(ConnectionState.ERROR)
                    log("⛔ ADB transport stopped [${code.name}]: $message. Auto-retry is forbidden until manual reconnection.")
                }
            }
            var published = false
            try {
                val connectStartedNs = System.nanoTime()
                if (!proto.connect()) {
                    diagnosticSessionTracker.recordMilestone("adb.connect.failed", (System.nanoTime() - connectStartedNs) / 1_000_000L)
                    if (generation == connectionGeneration.get()) _connectionState.postValue(ConnectionState.ERROR)
                    return
                }
                diagnosticSessionTracker.recordMilestone("adb.connect", (System.nanoTime() - connectStartedNs) / 1_000_000L)
                if (viewModelCleared.get() || generation != connectionGeneration.get()) return

                adbProtocol = proto
                published = true
                _fastbootDiagnostics.postValue(null)
                _fastbootPartitionInventory.postValue(null)
                _adbPeerMode.postValue(proto.peerMode)
                verifyPendingSideloadIfReady(proto)
                if (proto.peerMode == AdbProtocol.PeerMode.RECOVERY) {
                    val serial = currentUsbSerial()
                    val boundary = if (serial != null) proto.captureRecoveryLogBoundary() else null
                    preparedRecoveryLog = if (serial != null && boundary != null) {
                        PreparedRecoveryLog(serial, boundary, System.currentTimeMillis())
                    } else null
                } else if (proto.peerMode != AdbProtocol.PeerMode.SIDELOAD) {
                    preparedRecoveryLog = null
                }
                _connectionState.postValue(ConnectionState.ADB)
                diagnosticSessionTracker.recordConnection("ADB", connectedDeviceInfo)
                persistSessionSummary()
                logConnectionStatus()
            } finally {
                if (!published || generation != connectionGeneration.get() || viewModelCleared.get()) {
                    if (adbProtocol === proto) adbProtocol = null
                    proto.disconnect()
                }
            }
        }
    }

    private suspend fun awaitNativeUsbfsIdle(): Boolean {
        while (true) {
            val state = NativeUsbfsBackend.backendState()
            if (!NativeUsbfsBackend.hasActiveTransfer && !state.nativeTransferActive) return true
            delay(TRANSPORT_IDLE_POLL_MS)
        }
    }

    /**
     * Two-phase transport shutdown. The Java UsbDeviceConnection is never
     * released while a blocking native USBFS call may still own submitted URBs.
     */
    private suspend fun shutdownCurrentTransportsSafely(reason: String): Boolean {
        val activeOperation = operationJob
        val serviceOperation = activeServiceOperation
        val nativeStateBefore = NativeUsbfsBackend.backendState()
        // isActive becomes false as soon as a Job enters Cancelling, while its
        // finally block or a blocking JNI call may still be running. Only
        // isCompleted proves that the operation no longer owns the transport.
        val needsDrain = serviceOperation != null || activeOperation?.isCompleted == false ||
            NativeUsbfsBackend.hasActiveTransfer || nativeStateBefore.nativeTransferActive

        if (needsDrain) {
            log("⏳ USB shutdown requested ($reason). First cancelling the operation and waiting for confirmed Native USBFS drain.")
            activeOperation?.cancel(CancellationException("Transport shutdown requested: $reason"))
            fastbootProtocol?.cancel()
            adbProtocol?.cancel()

            val clean = withTimeoutOrNull(TRANSPORT_SHUTDOWN_TIMEOUT_MS) {
                activeOperation?.join()
                serviceOperation?.finished?.await()
                awaitNativeUsbfsIdle()
            } == true

            if (!clean) {
                transportRestartRequired.set(true)
                log("⛔ Safe USB shutdown was not confirmed within ${TRANSPORT_SHUTDOWN_TIMEOUT_MS} ms. UsbDeviceConnection will not be closed; new connections are blocked until NekoFlash is fully restarted.")
                _connectionState.postValue(ConnectionState.ERROR)
                return false
            }
        }

        val nativeStateAfter = NativeUsbfsBackend.backendState()
        if (!UsbTransportShutdownPolicy.canCloseUsb(
                kotlinTransferActive = NativeUsbfsBackend.hasActiveTransfer,
                nativeTransferActive = nativeStateAfter.nativeTransferActive
            )
        ) {
            transportRestartRequired.set(true)
            log("⛔ Native USBFS still reports an active transfer after waiting. USB close is blocked until the app is restarted.")
            _connectionState.postValue(ConnectionState.ERROR)
            return false
        }

        val fastbootClosed = fastbootProtocol?.disconnect() ?: true
        if (!fastbootClosed) {
            transportRestartRequired.set(true)
            log("⛔ FastbootProtocol refused to close USB before confirmed drain. New connections are blocked until NekoFlash is restarted.")
            _connectionState.postValue(ConnectionState.ERROR)
            return false
        }
        adbProtocol?.disconnect()
        fastbootProtocol = null
        adbProtocol = null
        operationJob = null
        operationGeneration.incrementAndGet()

        // At this point any blocking operation has returned and its finally block
        // already had a chance to release these resources. The calls below only
        // clear stale state left by an interrupted non-native operation.
        releaseOperationWakeLock(logRelease = false)
        _operationActive.postValue(false)
        return true
    }

    private fun publishDisconnectedState(reason: String) {
        if (activeTransportSessionId != null) {
            diagnosticSessionTracker.recordTermination(reason)
            flushDiagnostics("DISCONNECT:$reason", terminal = false)
        }
        pendingUsbTargetKey = null
        connectedUsbTarget = null
        connectedUsbManager = null
        connectedDeviceInfo = null
        activeTransportSessionId = null
        _transportSessionId.postValue(null)
        _connectionInfo.postValue(null)
        _fastbootDiagnostics.postValue(null)
        _fastbootPartitionInventory.postValue(null)
        _adbPeerMode.postValue(null)
        _operationActive.postValue(false)
        _connectionState.postValue(ConnectionState.NONE)
        diagnosticSessionTracker.recordConnection("NONE", reason)
        persistSessionSummary()
    }

    fun currentUsbMode(): UsbDeviceInspector.Mode? = connectedUsbTarget?.mode
    fun currentUsbLogicalSignature(): String? = connectedUsbTarget?.logicalSignature
    fun currentUsbVendorId(): Int? = connectedUsbTarget?.device?.vendorId

    fun isCurrentUsbDevice(device: UsbDevice): Boolean {
        val current = connectedUsbTarget?.device ?: return false
        return current.deviceName == device.deviceName ||
            (current.deviceId == device.deviceId &&
                current.vendorId == device.vendorId &&
                current.productId == device.productId)
    }

    private fun buildDeviceInfo(candidate: UsbDeviceInspector.Candidate): String {
        val device = candidate.device
        val mode = candidate.mode.name
        val name = device.productName ?: device.deviceName
        return "Mode: $mode | Device: $name | VID=${device.vendorId} | PID=${device.productId} | " +
            "interface=${candidate.interfaceIndex} | match=${candidate.matchKind.label}"
    }

    private fun setOperationSteps(steps: List<OperationStep>) {
        // Preserve the complete operation; the RecyclerView controls how many rows
        // are rendered at once. Truncating the model silently loses real steps.
        val completeSteps = steps.toList()
        synchronized(operationStepLock) { operationStepSnapshot = completeSteps }
        _operationSteps.postValue(completeSteps)
    }

    private fun markOperationStep(index: Int, status: OperationStepStatus, subtitle: String? = null) {
        val updated = synchronized(operationStepLock) {
            operationStepSnapshot.map { step ->
                if (step.index == index) step.copy(status = status, subtitle = subtitle ?: step.subtitle) else step
            }.also { operationStepSnapshot = it }
        }
        _operationSteps.postValue(updated)
    }

    fun logConnectionStatus() {
        log(text(R.string.connection_status_header))

        val displayMode = when {
            fastbootProtocol?.isConnected == true -> {
                val fastbootd = when (fastbootProtocol?.currentDiagnostics()?.isUserspace?.trim()?.lowercase(Locale.ROOT)) {
                    "yes" -> true
                    "no" -> false
                    else -> null
                }
                ConnectionModeUiPolicy.resolve(ConnectionModeUiPolicy.Transport.FASTBOOT, fastbootd = fastbootd)
            }
            adbProtocol?.isConnected == true -> {
                val adbMode = when (adbProtocol?.peerMode) {
                    AdbProtocol.PeerMode.DEVICE -> ConnectionModeUiPolicy.AdbMode.SYSTEM
                    AdbProtocol.PeerMode.RECOVERY -> ConnectionModeUiPolicy.AdbMode.RECOVERY
                    AdbProtocol.PeerMode.SIDELOAD -> ConnectionModeUiPolicy.AdbMode.SIDELOAD
                    AdbProtocol.PeerMode.UNKNOWN, null -> ConnectionModeUiPolicy.AdbMode.UNKNOWN
                }
                ConnectionModeUiPolicy.resolve(ConnectionModeUiPolicy.Transport.ADB, adbMode = adbMode)
            }
            else -> when (_connectionState.value ?: ConnectionState.NONE) {
                ConnectionState.NONE -> ConnectionModeUiPolicy.DisplayMode.NO_DEVICE
                ConnectionState.CONNECTING -> ConnectionModeUiPolicy.DisplayMode.CONNECTING
                ConnectionState.FASTBOOT -> ConnectionModeUiPolicy.DisplayMode.FASTBOOT_UNKNOWN
                ConnectionState.ADB -> ConnectionModeUiPolicy.DisplayMode.ADB_UNKNOWN
                ConnectionState.ERROR -> ConnectionModeUiPolicy.DisplayMode.ERROR
            }
        }

        val modeLabel = ConnectionModeUiPolicy.logLabel(displayMode)
        log(text(R.string.state_label, modeLabel))

        val deviceInfo = connectedDeviceInfo
        if (deviceInfo == null) {
            log(text(R.string.device_not_connected))
        } else {
            val details = deviceInfo.substringAfter(" | ", missingDelimiterValue = "")
            log(if (details.isBlank()) "Mode: $modeLabel" else "Mode: $modeLabel | $details")
        }

        adbProtocol?.takeIf { it.isConnected }?.let { log("ADB peer mode: ${it.peerMode.name}") }
        log(text(R.string.log_auto_saved, logFile?.absolutePath ?: text(R.string.log_folder_not_ready)))
    }

    fun currentLogFile(): File? = logStore?.currentCompactFile() ?: logFile
    fun currentLogFiles(): List<File> = logStore?.compactFiles().orEmpty()
    fun currentTraceLogFiles(): List<File> = logStore?.traceFiles().orEmpty()
    fun currentSessionSummaryFile(): File? = synchronized(logLock) {
        flushSuppressedDuplicatesLocked()
        persistSessionSummary()
    }
    fun currentDiagnosticSessionSummary(): DiagnosticSessionTracker.Snapshot = synchronized(logLock) {
        flushSuppressedDuplicatesLocked()
        diagnosticSessionTracker.snapshot()
    }
    fun logSnapshot(): List<String> = synchronized(logLock) {
        flushSuppressedDuplicatesLocked()
        lines.toList()
    }
    fun currentConnectionInfo(): String? = connectedDeviceInfo
    fun currentFastbootDiagnostics(): FastbootProtocol.DeviceDiagnostics? = fastbootProtocol?.currentDiagnostics()
    fun currentFastbootPartitionInventory(): FastbootPartitionInventory.Snapshot? =
        _fastbootPartitionInventory.value
    fun currentAdbDiagnostics(): AdbProtocol.DeviceDiagnostics? = adbProtocol?.currentDiagnostics()
    fun currentTransportSessionId(): String? = activeTransportSessionId
    fun currentBuildId(): String = BuildConfig.BUILD_ID

    fun setDebugLogging(enabled: Boolean) {
        debugLoggingEnabled = enabled
        fastbootProtocol?.debugLogging = enabled
        log(if (enabled) text(R.string.debug_enabled) else text(R.string.debug_disabled))
    }

    fun refreshFastbootDiagnostics() {
        startOperation(text(R.string.notif_fastboot_diagnostics), text(R.string.notif_updating_device), heavy = false) {
            val proto = fastbootProtocol
            if (proto?.isConnected == true) {
                val diagnostics = proto.refreshDiagnostics(force = true)
                _fastbootDiagnostics.postValue(diagnostics)

                // getvar:all is intentionally tied to a manual refresh only. It is
                // not executed during initial connection because some bootloaders
                // (notably onyx) have an unstable first Fastboot response over OTG.
                val inventoryStartedNs = System.nanoTime()
                val inventory = if (!proto.isSessionBroken) {
                    proto.collectPartitionInventory(diagnostics)
                } else {
                    null
                }
                diagnosticSessionTracker.recordMilestone(
                    "fastboot.partition-inventory",
                    (System.nanoTime() - inventoryStartedNs) / 1_000_000L
                )
                if (inventory != null) {
                    _fastbootPartitionInventory.postValue(inventory)
                    val topology = when (inventory.topology) {
                        FastbootPartitionInventory.SlotTopology.LEGACY_A_ONLY -> "legacy A-only (without A/B)"
                        FastbootPartitionInventory.SlotTopology.A_B -> "A/B"
                        FastbootPartitionInventory.SlotTopology.UNKNOWN -> "unknown"
                    }
                    val incomplete = inventory.entries.count { it.missingFields.isNotEmpty() }
                    log(
                        "ℹ️ Partition inventory: ${inventory.entries.size}, " +
                            "topology=$topology, incomplete=$incomplete, " +
                            "point-queries=${inventory.pointQueryCount}, status=${inventory.finalStatus}"
                    )
                    // All non-informational warnings must be retained in diagnostics.
                    inventory.warnings
                        .filter { it.severity != FastbootPartitionInventory.WarningSeverity.INFO }
                        .forEach { warning -> log("⚠️ Inventory ${warning.code}: ${warning.message}") }
                } else {
                    _fastbootPartitionInventory.postValue(null)
                    if (!proto.isSessionBroken) {
                        log("⚠️ getvar:all did not provide inventory; point Fastboot diagnostics were saved.")
                    }
                }

                if (proto.isSessionBroken) {
                    _connectionState.postValue(ConnectionState.ERROR)
                    failOperation("Fastboot session lost synchronization while refreshing data. Reconnect the device.")
                }
            } else {
                val message = text(R.string.error_no_fastboot)
                log(message)
                failOperation(message)
            }
        }
    }

    // ─── ВЫПОЛНЕНИЕ КОМАНД ───────────────────────────────────────────────────

    /**
     * Полный процесс разблокировки загрузчика Xiaomi (официальный Mi Unlock).
     * Требует: устройство в fastboot + авторизованный Mi-аккаунт (auth).
     * Шаги: чтение product+token с устройства → nonce → clear → ahaUnlock →
     * staging encryptData + oem unlock.
     */
    fun runMiUnlock(
        auth: MiAccountClient.AuthResult,
        onClearInfo: (String, Boolean) -> Unit,
        onAuthExpired: () -> Unit = {}
    ) {
        startOperation(text(R.string.notif_fastboot_command), "Mi Unlock", heavy = true) {
            val proto = fastbootProtocol
            if (proto?.isConnected != true) {
                val message = "Device is not in Fastboot mode. Put it into Fastboot and connect it over OTG."
                log("❌ $message")
                failOperation(message)
            }

            log("🔍 Reading device data...")
            val product = proto.getVar("product")?.replace(Regex("\\s"), "")
            if (product.isNullOrEmpty()) {
                val message = "Could not read device product"
                log("❌ $message")
                failOperation(message)
            }
            log("📱 product: $product")

            val serial = proto.currentDiagnostics()?.serialno?.trim()?.takeIf { it.isNotBlank() }
            val deviceToken = proto.readXiaomiUnlockToken()
            if (deviceToken.isNullOrEmpty()) {
                val message = "Could not read device token"
                log("❌ $message")
                failOperation(message)
            }
            log("🔑 deviceToken received (${deviceToken.length} characters)")

            // Новая явная попытка отменяет только старый незавершённый verify-marker.
            clearPendingUnlockVerification()

            try {
                val client = MiUnlockClient(
                    host = auth.host,
                    ssecurity = auth.ssecurity,
                    serviceCookies = auth.serviceCookies,
                    userId = auth.userId,
                    deviceId = auth.deviceId
                )
                log("🌐 Mi Unlock transport: migate-auth + signed-query v3 (${BuildConfig.VERSION_NAME})")
                log("🌐 Requesting nonce from the Mi server...")
                val nonce = client.getNonce()

                log("🌐 Checking device...")
                val clearInfo = client.checkClear(product, nonce)
                if (clearInfo.notice.isNotEmpty()) log("ℹ️ ${clearInfo.notice}")
                log(if (clearInfo.clearsData) "⚠️ Unlocking WILL ERASE device data" else "ℹ️ Data will not be erased")
                postMainThread { onClearInfo(clearInfo.notice, clearInfo.clearsData) }

                log("🌐 Requesting unlock from the Mi server...")
                val encryptDataHex = client.requestUnlock(product, deviceToken, nonce)
                log("✅ Server returned unlock data")

                val bytes = hexToBytes(encryptDataHex)
                val file = File(getApplication<Application>().filesDir, "encryptData")
                file.outputStream().use { it.write(bytes) }
                val accepted = try {
                    proto.stageAndOemUnlock(file)
                } finally {
                    runCatching { file.delete() }
                }

                if (!accepted) {
                    val message = "Unlock failed at the device stage"
                    log("❌ $message")
                    failOperation(message)
                }

                persistPendingUnlockVerification(product, serial)
                log("✅ The oem unlock command was accepted by the device.")
                log("🔎 Final success will be confirmed only after a new Fastboot connection and getvar:unlocked=yes.")
                verificationPending("Unlock command accepted. Waiting for reconnect to verify unlocked=yes.")
            } catch (abort: OperationAbort) {
                throw abort
            } catch (e: MiUnlockClient.SessionExpiredException) {
                val message = "Mi session expired or was revoked (HTTP 401). Sign in to Mi Account again."
                log("❌ $message")
                postMainThread { onAuthExpired() }
                failOperation(message)
            } catch (e: MiUnlockClient.BusinessException) {
                val message = e.message ?: "Xiaomi code ${e.code}"
                log("❌ Unlock error: $message")
                if (e.code == 20045) {
                    log("💡 Code 20045: check dataCenterZone. Current zone: ${auth.dataCenterZone}; choose another zone manually and retry only after checking the account region.")
                } else {
                    log("💡 See the exact reason above. Check the official Mi Unlock device status.")
                }
                failOperation(message)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("❌ Unlock error: $msg")
                val hasSpecificReason = msg.contains("Xiaomi code") ||
                    msg.contains("Xiaomi:") ||
                    msg.contains("code ")
                if (!hasSpecificReason) {
                    log("💡 Server did not provide a specific reason. Check the network, disable VPN/Private DNS, and repeat the safe step.")
                } else {
                    log("💡 See the exact reason above. Check the official Mi Unlock device status.")
                }
                failOperation(msg)
            }
        }
    }


    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.isNotEmpty()) { "Hex payload is empty" }
        require(clean.length % 2 == 0) { "Hex must have even length" }
        return ByteArray(clean.length / 2) { index ->
            val high = Character.digit(clean[index * 2], 16)
            val low = Character.digit(clean[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Invalid hex payload at byte $index" }
            ((high shl 4) + low).toByte()
        }
    }

    private fun postMainThread(block: () -> Unit) {
        mainHandler.post(block)
    }

    fun runFastbootCommand(cmd: String, heavy: Boolean = true, expectedSerial: String? = null) {
        startOperation(
            text(R.string.notif_fastboot_command),
            text(R.string.notif_executing, cmd),
            heavy = heavy,
            expectedFastbootSerial = expectedSerial
        ) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val ok = if (heavy) proto.sendCommand(cmd) else proto.runTerminalCommand(cmd)
            if (!ok) failOperation(text(R.string.rev11_fastboot_command_failed, cmd))
        }
    }

    internal fun runFastbootTerminalPlan(steps: List<FastbootExecutionStep>, expectedSerial: String? = null) {
        val heavy = steps.any { it !is FastbootExecutionStep.Raw && it !is FastbootExecutionStep.Inspect && it !is FastbootExecutionStep.PartitionCommand }
        startOperation(text(R.string.notif_fastboot_command), text(R.string.rev21_preparing_plan),
            heavy = heavy, expectedFastbootSerial = expectedSerial) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val context = kotlinx.coroutines.currentCoroutineContext()
            val cache = getApplication<Application>().cacheDir
            val needsImages = steps.any { it is FastbootExecutionStep.Format || it is FastbootExecutionStep.Wipe || it is FastbootExecutionStep.WipeSuper }
            // Admission is exclusive. Remove private images left by a killed process before creating this job's directory.
            if (needsImages) cache.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("fastboot-host-[0-9a-f-]{36}")) }
                ?.forEach { context.ensureActive(); check(it.deleteRecursively()) { "Could not remove interrupted Fastboot images" } }
            val directory = if (needsImages) File(cache, "fastboot-host-${UUID.randomUUID()}") else cache
            if (needsImages) check(directory.mkdirs()) { "Could not create private Fastboot image workspace" }
            try {
                val port = object : FastbootPlanPort {
                    override fun getVar(name: String) = proto.getVar(name)
                    override fun downloadLimitBytes() = proto.hostDownloadLimitBytes()
                    override fun targets(partition: String, slot: String?) = proto.resolveSlotPartitionTargets(partition, slot)
                        ?: failOperation(text(R.string.rev13_fastboot_slot_error, partition))
                    override fun command(command: String) = proto.runTerminalCommand(command)
                    override fun flash(partition: String, file: File) {
                        val result = proto.flashPartitionDetailed(partition, file)
                        if (!result.success) failOperation(formatFlashFailure(partition, result))
                    }
                    override fun download(file: File, command: String) {
                        if (!proto.downloadAndRun(file, command)) failOperation(text(R.string.rev11_fastboot_download_failed, command))
                    }
                    override fun fetch(partition: String, file: File) {
                        if (!proto.fetchPartition(partition, file)) failOperation("Fastboot fetch failed: $partition")
                    }
                    override fun inspect(partition: String) {
                        if (proto.inspectLogicalPartition(partition) == null) failOperation(text(R.string.rev11_fastboot_logical_info_failed, partition))
                    }
                }
                val formatter = FastbootFilesystemFormatter(nextData = AndroidImageExtents::nextData, log = ::log)
                FastbootPlanExecutor(port, formatter, directory, { context.ensureActive() }, ::log) { detail ->
                    postOperationProgress(OperationProgress(text(R.string.notif_fastboot_command), -1, detail))
                }.run(steps)
            } finally {
                if (needsImages && !directory.deleteRecursively()) log("Could not remove temporary Fastboot images: ${directory.name}")
            }
        }
    }


    /** A slot switch is complete only when the bootloader reports it back. */
    fun setActiveSlotAndVerify(slot: String) {
        require(slot == "a" || slot == "b") { "Invalid slot" }
        startOperation(
            text(R.string.notif_fastboot_command),
            text(R.string.notif_executing, "set_active:$slot")
        ) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            if (!proto.sendCommand("set_active:$slot")) failOperation(text(R.string.rev11_slot_switch_failed, slot))
            val actual = proto.getVar("current-slot")?.trim()?.removePrefix("_")
                ?.lowercase(Locale.US)
            if (actual != slot) {
                failOperation(text(R.string.rev11_slot_switch_unverified, slot, actual ?: text(R.string.device_bool_unknown)))
            }
            proto.currentDiagnostics()?.let { _fastbootDiagnostics.postValue(it) }
            log("✅ " + text(R.string.rev11_slot_switch_verified, slot))
        }
    }

    fun runFastbootDownloadAndRun(file: File, commandAfterDownload: String, expectedSerial: String? = null) {
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, commandAfterDownload), expectedFastbootSerial = expectedSerial) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.downloadAndRun(file, commandAfterDownload)) {
                failOperation(text(R.string.rev11_fastboot_download_failed, commandAfterDownload))
            }
        }
    }


    fun runFastbootLogicalPartitionCommand(command: String) {
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, command)) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.runLogicalPartitionCommand(command)) failOperation(text(R.string.rev11_fastboot_logical_failed, command))
        }
    }

    fun inspectFastbootLogicalPartition(partition: String, expectedSerial: String? = null) {
        startOperation(text(R.string.notif_fastboot_diagnostics), text(R.string.notif_updating_device), heavy = false, expectedFastbootSerial = expectedSerial) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (proto.inspectLogicalPartition(partition) == null) {
                failOperation(text(R.string.rev11_fastboot_logical_info_failed, partition))
            }
        }
    }

    fun runFastbootFetch(partition: String, outputFile: File, slot: String? = null, expectedSerial: String? = null) {
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, "fetch $partition"), expectedFastbootSerial = expectedSerial) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            val targets = proto.resolveSlotPartitionTargets(partition, slot)
                ?: failOperation("Could not apply Fastboot slot for $partition")
            if (targets.size > 1) failOperation("Fastboot fetch for multiple slots requires separate output files")
            val target = targets.firstOrNull() ?: failOperation("Fastboot fetch: no suitable partition for $partition")
            if (!proto.fetchPartition(target, outputFile)) failOperation("Fastboot fetch failed: $target")
        }
    }

    fun runFastbootPartitionCommand(wirePrefix: String, partition: String, slot: String? = null, expectedSerial: String? = null) {
        val label = "$wirePrefix $partition" + slot?.let { " --slot=$it" }.orEmpty()
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, label), heavy = false, expectedFastbootSerial = expectedSerial) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val targets = proto.resolveSlotPartitionTargets(partition, slot)
                ?: failOperation("Could not apply Fastboot slot for $partition")
            targets.forEach { target ->
                val wire = "$wirePrefix:$target"
                if (!proto.sendCommand(wire)) failOperation("Fastboot $wirePrefix failed: $target")
            }
        }
    }

    fun runAdbService(service: String) {
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, service)) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.runService(service)) failOperation("ADB service failed: $service")
            if (AdbServiceCompletionPolicy.expectsOneWayDisconnect(service)) {
                postOperationProgress(
                    OperationProgress(
                        title = text(R.string.notif_adb_command),
                        percent = 100,
                        detail = "Reboot command sent. The device will temporarily disconnect and appear in the new mode."
                    )
                )
            }
        }
    }

    fun runAdbShell(command: String) {
        val interactive = command.isBlank()
        val label = if (interactive) "interactive shell" else "shell $command"
        // ВАЖНО: интерактивный shell — долгоживущий канал, в который пользователь
        // вводит команды через нижнюю строку. Ему НЕ нужен блокирующий полноэкранный
        // прогресс-диалог (heavy=true): диалог перекрывал ввод и после закрытия
        // shell (exit/CLOSED) продолжал висеть, а его «Отмена» не убирала его.
        // Запускаем как лёгкую операцию — терминал остаётся доступен, выход через
        // exit / adb shell-stop / кнопку Стоп. Разовая команда (adb shell <cmd>) —
        // по-прежнему heavy (короткая, ждём результат).
        startOperation(
            text(R.string.notif_adb_command),
            text(R.string.notif_executing, label),
            heavy = !interactive
        ) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.runShellCommand(command)) failOperation("ADB shell failed: $label")
        }
    }

    fun isInteractiveAdbShellActive(): Boolean = adbProtocol?.hasInteractiveShell == true

    fun sendInteractiveAdbShellBytes(payload: ByteArray): Boolean {
        val proto = adbProtocol
        return if (proto?.isConnected == true && proto.hasInteractiveShell) {
            proto.sendInteractiveShellBytes(payload)
        } else {
            log(text(R.string.rev15_shell_not_open))
            false
        }
    }

    fun sendInteractiveAdbShellInput(line: String) {
        val proto = adbProtocol
        if (proto?.isConnected == true && proto.hasInteractiveShell) {
            proto.sendInteractiveShellInput(line)
        } else {
            log(text(R.string.rev15_shell_not_open))
        }
    }

    fun interruptInteractiveAdbShell() {
        val proto = adbProtocol
        if (proto?.isConnected == true && proto.hasInteractiveShell) {
            proto.sendInteractiveShellInterrupt()
        } else {
            log(text(R.string.rev15_shell_not_open))
        }
    }

    fun sendInteractiveAdbShellEof() {
        val proto = adbProtocol
        if (proto?.isConnected == true && proto.hasInteractiveShell) {
            proto.sendInteractiveShellEof()
        } else {
            log(text(R.string.rev15_shell_not_open))
        }
    }

    fun stopInteractiveAdbShell() {
        val proto = adbProtocol
        if (proto?.isConnected == true && proto.hasInteractiveShell) {
            proto.stopInteractiveShell()
        } else {
            log(text(R.string.rev15_shell_already_closed))
        }
    }

    fun runAdbPush(localFile: File, remotePath: String) {
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "push ${localFile.name} $remotePath")) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.pushPath(localFile, remotePath)) failOperation("ADB push failed")
        }
    }

    fun runAdbPull(remotePath: String, localFile: File) {
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "pull $remotePath")) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.pullFile(remotePath, localFile)) failOperation("ADB pull failed")
        }
    }

    fun runAdbInstall(packageFile: File, options: List<String>) {
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "install ${packageFile.name}")) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.installPackage(packageFile, options)) failOperation("APK installation failed")
        }
    }

    fun runAdbInstallMultiple(apkFiles: List<File>, options: List<String>) {
        val names = apkFiles.joinToString(" ") { it.name }
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "install-multiple $names")) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.installMultipleApks(apkFiles, options)) failOperation("install-multiple failed")
        }
    }

    fun runFlash(partition: String, file: File, slot: String? = null, expectedSerial: String? = null) =
        runFlash(partition, FirmwareSource.WorkspaceFile(file), slot, expectedSerial)

    internal fun runFlash(partition: String, source: FirmwareSource, slot: String? = null, expectedSerial: String? = null) {
        val label = partition + slot?.let { " --slot=$it" }.orEmpty()
        startOperation(
            text(R.string.notif_flash_img),
            text(R.string.notif_flashing_partition, source.displayName, label),
            expectedFastbootSerial = expectedSerial
        ) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val targets = proto.resolveSlotPartitionTargets(partition, slot)
                ?: failOperation(text(R.string.rev13_fastboot_slot_error, partition))
            targets.forEach { target ->
                val result = when (source) {
                    is FirmwareSource.WorkspaceFile ->
                        proto.flashPartitionDetailed(target, source.file)
                    is FirmwareSource.SafDocument ->
                        proto.flashPartitionDetailed(
                            target, source, getApplication<Application>().contentResolver
                        )
                }
                if (!result.success) {
                    if (source is FirmwareSource.SafDocument &&
                        result.stage == FastbootProtocol.FlashStage.VALIDATION
                    ) {
                        logFileOnly("Fastboot SAF preflight: ${result.message}")
                        failOperation(text(R.string.rev13_direct_saf_failed))
                    }
                    failOperation(formatFlashFailure(target, result))
                }
            }
        }
    }


    private fun formatFlashFailure(partition: String, result: FastbootProtocol.FlashResult): String {
        return buildString {
            append("Flashing $partition failed")
            append(" [stage=${result.stage}, kind=${result.failureKind}]")
            if (result.message.isNotBlank()) append(": ${result.message}")
            if (result.sessionCorrupted) append(". A full target-device re-entry into Fastboot is required")
        }
    }

    fun currentFlashOperationDraft(): FlashOperationDraft = synchronized(flashDraftLock) {
        flashDraftSnapshot
    }

    fun addFlashQueueFile(partition: String, file: File) {
        val item = runCatching { FlashOperationDraftPolicy.createItem(partition, file) }
            .getOrElse { error ->
                log("❌ Could not add file to the queue: ${error.message ?: error.javaClass.simpleName}")
                return
            }
        val next = runCatching { FlashOperationDraftPolicy.upsert(currentFlashOperationDraft(), item) }
            .getOrElse { error ->
                log("❌ Could not update queue: ${error.message ?: error.javaClass.simpleName}")
                return
            }
        publishFlashOperationDraft(next, persist = true)
        log(text(R.string.flash_queue_updated_log, item.partition, item.displayName))
    }

    fun moveFlashQueueDraftItem(partition: String, direction: Int) {
        val next = FlashOperationDraftPolicy.move(currentFlashOperationDraft(), partition, direction)
        publishFlashOperationDraft(next, persist = true)
    }

    fun removeFlashQueueDraftItem(partition: String) {
        val next = FlashOperationDraftPolicy.remove(currentFlashOperationDraft(), partition)
        publishFlashOperationDraft(next, persist = true)
    }

    fun clearFlashQueueDraft() {
        publishFlashOperationDraft(
            FlashOperationDraftPolicy.clear(currentFlashOperationDraft()),
            persist = true
        )
        log(text(R.string.flash_queue_cleared_log))
    }

    fun executeFlashQueueDraft() {
        val draft = currentFlashOperationDraft()
        if (draft.items.isEmpty()) {
            log(text(R.string.flash_queue_empty_log))
            return
        }
        val executionItems = ArrayList<FlashQueueItem>(draft.items.size)
        for (item in draft.items) {
            val file = FlashOperationDraftPolicy.resolve(item)
            if (file == null) {
                log("❌ " + text(R.string.rev18_queue_source_changed, item.partition, item.displayName))
                return
            }
            executionItems += FlashQueueItem(item.partition, file, item.expectedSizeBytes)
        }
        runFlashQueue(executionItems)
    }

    private fun publishFlashOperationDraft(draft: FlashOperationDraft, persist: Boolean) {
        synchronized(flashDraftLock) { flashDraftSnapshot = draft }
        if (persist) {
            runCatching { recoveryStore.writeDraft(draft) }
                .onFailure { log(text(R.string.rev20_draft_save_failed, it.message ?: it.javaClass.simpleName)) }
        }
        _flashOperationDraft.value = draft
    }

    private fun runFlashQueue(items: List<FlashQueueItem>) {
        val queue = items.filter { it.partition.isNotBlank() }
        if (queue.isEmpty()) { log(text(R.string.flash_queue_empty_log)); return }

        // Never reorder operator-defined targets. Execution order must be visible order.
        val ordered = queue

        startOperation(text(R.string.notif_flash_img), "Flash queue: ${ordered.size} item(s) Do not disconnect the cable.") {
            setOperationSteps(ordered.mapIndexed { index, item ->
                OperationStep(
                    index = index + 1,
                    total = ordered.size,
                    title = "flash ${item.partition} ← ${item.file.name}",
                    subtitle = formatBytesShort(item.file.length()),
                    status = OperationStepStatus.PENDING
                )
            })
            val proto = fastbootProtocol
            if (proto?.isConnected != true) {
                markOperationStep(1, OperationStepStatus.FAILED, text(R.string.error_no_fastboot))
                failOperation("No Fastboot connection")
            }
            ordered.forEachIndexed { index, item ->
                val stepNumber = index + 1
                if (!item.file.isFile || !item.file.canRead() || item.file.length() != item.expectedSizeBytes) {
                    val reason = text(R.string.rev18_queue_source_changed, item.partition, item.file.name)
                    markOperationStep(stepNumber, OperationStepStatus.FAILED, reason)
                    failOperation(reason)
                }
                markOperationStep(stepNumber, OperationStepStatus.RUNNING, "fastboot flash ${item.partition}")
                log("=== FLASH QUEUE ${stepNumber}/${ordered.size}: ${item.partition} ← ${item.file.name} ===")
                val result = proto.flashPartitionDetailed(item.partition, item.file)
                val diagnostics = proto.currentDiagnostics()
                if (diagnostics != null) _fastbootDiagnostics.postValue(diagnostics)
                markOperationStep(
                    stepNumber,
                    if (result.success) OperationStepStatus.OK else OperationStepStatus.FAILED,
                    diagnosticsBrief(diagnostics)
                )
                if (!result.success) {
                    log("❌ Queue stopped at partition ${item.partition}")
                    failOperation(formatFlashFailure(item.partition, result))
                }
            }
            log("✅ Flash queue completed")
        }
    }




    private fun formatBytesShort(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.2f %s", value, units[unit])
    }

    private fun diagnosticsBrief(diagnostics: FastbootProtocol.DeviceDiagnostics?): String {
        if (diagnostics == null) return "none"
        val parts = mutableListOf<String>()
        diagnostics.product?.takeIf { it.isNotBlank() }?.let { parts += "product=$it" }
        diagnostics.currentSlot?.takeIf { it.isNotBlank() }?.let { parts += "slot=$it" }
        diagnostics.unlocked?.takeIf { it.isNotBlank() }?.let { parts += "unlocked=$it" }
        diagnostics.secure?.takeIf { it.isNotBlank() }?.let { parts += "secure=$it" }
        diagnostics.antiRollback?.takeIf { it.isNotBlank() }?.let { parts += "anti=$it" }
        diagnostics.isUserspace?.takeIf { it.isNotBlank() }?.let { parts += "is-userspace=$it" }
        diagnostics.superPartitionName?.takeIf { it.isNotBlank() }?.let { parts += "super=$it" }
        return if (parts.isEmpty()) "empty" else parts.joinToString(", ")
    }


    private fun unlockVerificationPrefs() =
        getApplication<Application>().getSharedPreferences(MI_UNLOCK_VERIFY_PREFS, Context.MODE_PRIVATE)

    private fun persistPendingUnlockVerification(
        product: String,
        serial: String?,
        expectedUnlocked: Boolean = true,
        operationLabel: String = "Mi Unlock"
    ) {
        unlockVerificationPrefs().edit {
            putString(MI_UNLOCK_VERIFY_PRODUCT, product.trim())
            putString(MI_UNLOCK_VERIFY_SERIAL, serial?.trim())
            putBoolean(MI_UNLOCK_VERIFY_EXPECTED_UNLOCKED, expectedUnlocked)
            putString(MI_UNLOCK_VERIFY_OPERATION_LABEL, operationLabel)
            putLong(MI_UNLOCK_VERIFY_CREATED_AT, System.currentTimeMillis())
        }
    }

    private fun readPendingUnlockVerification(): PendingUnlockVerification? {
        val prefs = unlockVerificationPrefs()
        val product = prefs.getString(MI_UNLOCK_VERIFY_PRODUCT, null).trimToNull() ?: return null
        return PendingUnlockVerification(
            product = product,
            serial = prefs.getString(MI_UNLOCK_VERIFY_SERIAL, null).trimToNull(),
            expectedUnlocked = prefs.getBoolean(MI_UNLOCK_VERIFY_EXPECTED_UNLOCKED, true),
            operationLabel = prefs.getString(MI_UNLOCK_VERIFY_OPERATION_LABEL, null).trimToNull() ?: "Mi Unlock",
            createdAtMs = prefs.getLong(MI_UNLOCK_VERIFY_CREATED_AT, 0L)
        )
    }

    private fun clearPendingUnlockVerification() {
        unlockVerificationPrefs().edit { clear() }
    }

    private fun currentUsbSerial(): String? =
        runCatching { connectedUsbTarget?.device?.serialNumber }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun sideloadVerificationPrefs() =
        getApplication<Application>().getSharedPreferences(SIDELOAD_VERIFY_PREFS, Context.MODE_PRIVATE)

    private fun persistPendingSideloadVerification(source: FirmwareSource): PendingSideloadVerification {
        val serial = currentUsbSerial()
        val now = System.currentTimeMillis()
        val prepared = preparedRecoveryLog
        preparedRecoveryLog = null // A boundary belongs to one accepted attempt only.
        val boundary = prepared?.takeIf {
            it.serial == serial && now - it.createdAtMs in 0L..SIDELOAD_VERIFY_TIMEOUT_MS
        }?.boundary
        val attempt = PendingSideloadVerification(
            UUID.randomUUID().toString(), source.displayName, source.sizeHint ?: -1L,
            serial, now, boundary, false
        )
        sideloadGuiAttemptId = attempt.attemptId
        @Suppress("UseKtx")
        val saved = sideloadVerificationPrefs().edit().clear()
            .putString(SIDELOAD_VERIFY_ATTEMPT_ID, attempt.attemptId)
            .putString(SIDELOAD_VERIFY_PACKAGE, attempt.packageName)
            .putLong(SIDELOAD_VERIFY_PACKAGE_SIZE, attempt.packageSize)
            .putString(SIDELOAD_VERIFY_SERIAL, serial)
            .putLong(SIDELOAD_VERIFY_CREATED_AT, now)
            .putString(SIDELOAD_VERIFY_BOOT_ID, boundary?.bootId)
            .putLong(SIDELOAD_VERIFY_LOG_BYTES, boundary?.byteCount ?: -1L)
            .putString(SIDELOAD_VERIFY_LOG_HASH, boundary?.sha256)
            .putBoolean(SIDELOAD_VERIFY_TRANSFER_FINISHED, false)
            .commit()
        if (!saved) log("⚠️ Could not save ADB Sideload attempt marker.")
        return attempt
    }

    private fun markSideloadTransferFinished(attemptId: String) {
        val prefs = sideloadVerificationPrefs()
        if (prefs.getString(SIDELOAD_VERIFY_ATTEMPT_ID, null) != attemptId) return
        @Suppress("UseKtx")
        val saved = prefs.edit().putBoolean(SIDELOAD_VERIFY_TRANSFER_FINISHED, true).commit()
        if (!saved) log("⚠️ Could not persist Sideload transfer completion.")
    }

    private fun readPendingSideloadVerification(): PendingSideloadVerification? {
        val prefs = sideloadVerificationPrefs()
        // Pre-REV19 markers have no attempt ID or unique device identity and cannot be upgraded into proof.
        val attemptId = prefs.getString(SIDELOAD_VERIFY_ATTEMPT_ID, null).trimToNull() ?: return null
        val packageName = prefs.getString(SIDELOAD_VERIFY_PACKAGE, null).trimToNull() ?: return null
        return PendingSideloadVerification(
            attemptId = attemptId,
            packageName = packageName,
            packageSize = prefs.getLong(SIDELOAD_VERIFY_PACKAGE_SIZE, -1L),
            serial = prefs.getString(SIDELOAD_VERIFY_SERIAL, null).trimToNull(),
            createdAtMs = prefs.getLong(SIDELOAD_VERIFY_CREATED_AT, 0L),
            boundary = RecoveryLogBoundary.parse(
                prefs.getString(SIDELOAD_VERIFY_BOOT_ID, null),
                prefs.getLong(SIDELOAD_VERIFY_LOG_BYTES, -1L).toString(),
                prefs.getString(SIDELOAD_VERIFY_LOG_HASH, null)
            ),
            transferFinished = prefs.getBoolean(SIDELOAD_VERIFY_TRANSFER_FINISHED, false)
        )
    }

    private fun clearPendingSideloadVerification(attemptId: String) {
        val prefs = sideloadVerificationPrefs()
        if (prefs.getString(SIDELOAD_VERIFY_ATTEMPT_ID, null) == attemptId) {
            prefs.edit { clear() }
        }
    }

    private fun adbBannerProperty(banner: String, key: String): String? {
        val marker = "$key="
        val start = banner.indexOf(marker)
        if (start < 0) return null
        return banner.substring(start + marker.length)
            .substringBefore(';')
            .trimToNull()
    }

    /**
     * A verified Recovery verdict finalizes the *same* pending Sideload GUI
     * operation. A recent unrelated operation must never be overwritten.
     * Unknown Recovery results remain unverified, never labelled SUCCESS.
     */
    private fun publishSideloadRecoveryVerdict(
        attemptId: String,
        message: String,
        outcome: OperationOutcomeKind
    ) {
        val current = _operationProgress.value
        val sideloadTitle = text(R.string.notif_adb_sideload)
        if (sideloadGuiAttemptId != attemptId || _operationActive.value == true || (
                current != null &&
                    !(current.title == sideloadTitle &&
                      current.outcome == OperationOutcomeKind.VERIFY_PENDING)
            )
        ) {
            logFileOnly("Recovery verdict saved in diagnostics; a different operation owns the GUI state.")
            return
        }
        postOperationProgress(
            OperationProgress(
                title = sideloadTitle,
                percent = if (outcome == OperationOutcomeKind.SUCCESS) 100 else -1,
                detail = message,
                finished = true,
                success = outcome == OperationOutcomeKind.SUCCESS,
                outcome = outcome
            )
        )
        persistSessionSummary()
    }

    private fun verifyPendingSideloadIfReady(proto: AdbProtocol) {
        val pending = readPendingSideloadVerification() ?: return
        if (proto.peerMode != AdbProtocol.PeerMode.RECOVERY || !pending.transferFinished) return

        val ageMs = System.currentTimeMillis() - pending.createdAtMs
        if (pending.createdAtMs <= 0L || ageMs < 0L || ageMs > SIDELOAD_VERIFY_TIMEOUT_MS) {
            clearPendingSideloadVerification(pending.attemptId)
            log("⚠️ ADB Sideload result wait timed out; check the install result on the Recovery screen.")
            return
        }

        if (pending.serial == null || pending.serial != currentUsbSerial()) {
            log(text(R.string.rev19_sideload_identity_unconfirmed))
            return
        }

        log("=== ADB SIDELOAD RESULT ===")
        log("Package: ${pending.packageName} (${pending.packageSize} bytes)")
        val verification = proto.inspectRecoveryInstallResult(pending.boundary)
        if (readPendingSideloadVerification()?.attemptId != pending.attemptId) return
        verification.source?.let { log("Recovery source: $it") }
        verification.evidence?.takeIf { it.isNotBlank() }?.let { log("Recovery evidence: $it") }

        when (verification.verdict) {
            RecoveryInstallVerifier.Verdict.SUCCESS -> {
                clearPendingSideloadVerification(pending.attemptId)
                log("✅ Recovery reports successful installation: ${verification.message}")
                publishSideloadRecoveryVerdict(
                    pending.attemptId,
                    "Recovery confirmed installation: ${verification.message}",
                    OperationOutcomeKind.SUCCESS
                )
            }
            RecoveryInstallVerifier.Verdict.FAILED -> {
                clearPendingSideloadVerification(pending.attemptId)
                val detail = buildString {
                    append(verification.message)
                    verification.evidence?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                log("❌ Recovery reports an install error: $detail")
                publishSideloadRecoveryVerdict(
                    pending.attemptId,
                    "Recovery installation failed: $detail",
                    OperationOutcomeKind.FAILED
                )
            }
            RecoveryInstallVerifier.Verdict.UNKNOWN -> {
                log(text(R.string.rev19_sideload_attempt_unconfirmed))
                logFileOnly("Sideload ${pending.attemptId}: ${verification.message}")
            }
        }
    }

    private fun verifyPendingUnlockIfReady(diagnostics: FastbootProtocol.DeviceDiagnostics) {
        val pending = readPendingUnlockVerification() ?: return
        val ageMs = System.currentTimeMillis() - pending.createdAtMs
        if (pending.createdAtMs <= 0L || ageMs < 0L || ageMs > MI_UNLOCK_VERIFY_TIMEOUT_MS) {
            clearPendingUnlockVerification()
            log("⚠️ Verification wait ${pending.operationLabel} timed out. Start the operation again.")
            return
        }

        val currentProduct = diagnostics.product?.trim()
        if (!currentProduct.equals(pending.product, ignoreCase = true)) {
            logFileOnly(
                "Pending ${pending.operationLabel} verification ignored for another product: " +
                    "pending=${pending.product}, current=${currentProduct ?: "unknown"}"
            )
            return
        }

        val currentSerial = diagnostics.serialno?.trim()?.takeIf { it.isNotBlank() }
        if (pending.serial != null) {
            if (currentSerial == null) {
                logFileOnly(
                    "Pending ${pending.operationLabel} verification waits for serial identity: " +
                        "pending=${pending.serial}, current=unknown"
                )
                return
            }
            if (!pending.serial.equals(currentSerial, ignoreCase = true)) {
                logFileOnly(
                    "Pending ${pending.operationLabel} verification ignored for another serial: " +
                        "pending=${pending.serial}, current=$currentSerial"
                )
                return
            }
        }

        val actualUnlocked = FastbootValueParser.parseBoolean(diagnostics.unlocked)
        val expectedText = if (pending.expectedUnlocked) "yes" else "no"
        when (actualUnlocked) {
            pending.expectedUnlocked -> {
                clearPendingUnlockVerification()
                val message = "${pending.operationLabel} confirmed by device: getvar:unlocked=$expectedText"
                log("🎉 $message")
                postOperationProgress(
                    OperationProgress(
                        title = pending.operationLabel,
                        percent = 100,
                        detail = message,
                        finished = true,
                        success = true,
                        outcome = OperationOutcomeKind.SUCCESS
                    )
                )
            }
            null -> log("ℹ️ ${pending.operationLabel} is waiting for final verification: getvar:unlocked is not available yet.")
            else -> {
                clearPendingUnlockVerification()
                val actualText = if (actualUnlocked) "yes" else "no"
                val message = "${pending.operationLabel} not confirmed: device reports getvar:unlocked=$actualText, expected $expectedText"
                log("❌ $message")
                postOperationProgress(
                    OperationProgress(
                        title = pending.operationLabel,
                        percent = 0,
                        detail = message,
                        finished = true,
                        success = false,
                        outcome = OperationOutcomeKind.FAILED
                    )
                )
            }
        }
    }

    fun runSideload(file: File) = runSideload(FirmwareSource.WorkspaceFile(file))

    internal fun runSideload(source: FirmwareSource) {
        startOperation(text(R.string.notif_adb_sideload), text(R.string.notif_sideload_sending, source.displayName)) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (proto.peerMode != AdbProtocol.PeerMode.SIDELOAD) {
                failOperation(text(R.string.rev12_sideload_mode_inactive, proto.peerMode.name))
            }
            // Admission comes first: a rejected second click cannot clear an active marker.
            val attempt = persistPendingSideloadVerification(source)
            var awaitingVerdict = false
            try {
                when (val result = proto.sideloadZip(source, getApplication<Application>().contentResolver)) {
                    AdbProtocol.SideloadResult.TransferComplete -> {
                        markSideloadTransferFinished(attempt.attemptId)
                        awaitingVerdict = true
                        verificationPending(text(R.string.rev12_sideload_verify_pending))
                    }
                    is AdbProtocol.SideloadResult.TransferClosedBeforeDoneDone -> {
                        markSideloadTransferFinished(attempt.attemptId)
                        awaitingVerdict = true
                        verificationPending(text(R.string.rev12_sideload_disconnect_pending, result.percent))
                    }
                    AdbProtocol.SideloadResult.Cancelled -> {
                        throw OperationAbort(OperationOutcome.Cancelled(text(R.string.rev12_sideload_cancelled)))
                    }
                    is AdbProtocol.SideloadResult.NotInSideloadMode -> {
                        failOperation(text(R.string.rev12_sideload_mode_inactive, result.mode.name))
                    }
                    is AdbProtocol.SideloadResult.Failed -> {
                        if (source is FirmwareSource.SafDocument && result.kind == AdbProtocol.SideloadFailureKind.FILE) {
                            logFileOnly("SAF direct read: ${result.message}")
                            failOperation(text(R.string.rev12_sideload_saf_unsupported))
                        }
                        failOperation(text(R.string.rev12_sideload_failed, result.kind.name, result.message))
                    }
                }
            } finally {
                if (!awaitingVerdict) clearPendingSideloadVerification(attempt.attemptId)
            }
        }
    }

    /**
     * Запускает ровно одну долгую USB-операцию. Новая операция не вытесняет
     * активную: сначала текущая должна завершиться либо пройти безопасную отмену
     * с подтверждённым возвратом native USBFS вызова.
     */
    private fun startOperation(
        title: String,
        text: String,
        heavy: Boolean = true,
        prepareFastbootSession: Boolean = true,
        expectedFastbootSerial: String? = null,
        block: suspend OperationContext.() -> Unit
    ) {
        synchronized(operationLaunchLock) {
            if (viewModelCleared.get()) {
                log("⚠️ New operation rejected: USB session is finishing safe shutdown.")
                return
            }
            if (transportRestartRequired.get()) {
                log("⛔ New operation is blocked: USB transport requires a full NekoFlash restart after unconfirmed cleanup.")
                return
            }
            if (connectionJob?.isCompleted == false) {
                log("⚠️ USB connect or disconnect is still in progress. Wait for a stable device status.")
                return
            }
            // A completed Job can still be delivering its completion callbacks.
            // The reference is cleared only after state/resources have been finalized.
            if (operationJob != null || NativeUsbfsBackend.hasActiveTransfer ||
                NativeUsbfsBackend.backendState().nativeTransferActive) {
                log("⚠️ Another USB operation is still active. Wait for it to finish first or perform a safe cancel.")
                return
            }
            val gen = operationGeneration.incrementAndGet()
            val id = UUID.randomUUID().toString()
            sideloadGuiAttemptId = null
            val pending = PersistedOperation(id, title, text, System.currentTimeMillis(), -1, PersistedOperationPhase.PREPARING)
            val result = AtomicReference<OperationOutcome>(OperationOutcome.Success)
            val task = ServiceOwnedOperation(
                id, title, text, heavy,
                cancelTransport = {
                    synchronized(operationLaunchLock) {
                        if (activeServiceOperation?.id == id) {
                            fastbootProtocol?.cancel()
                            adbProtocol?.cancel()
                        }
                    }
                },
                onFinished = { completed, cause ->
                    finishServiceOperation(completed, gen, result.get(), cause)
                }
            ) {
                val context = OperationContext()
                try {
                    // This body can start only after the service acknowledges startForeground.
                    synchronized(checkpointLock) {
                        val running = pending.copy(phase = PersistedOperationPhase.RUNNING)
                        recoveryStore.writeOperation(running)
                        persistedOperation = running
                        lastCheckpointAtMs = SystemClock.elapsedRealtime()
                    }
                    coroutineContext.ensureActive()
                    acquireOperationWakeLock()
                    val fastboot = fastbootProtocol
                    if (prepareFastbootSession && fastboot?.isConnected == true && !fastboot.beginOperation()) {
                        context.failOperation("Fastboot session is not ready for a new operation. Reconnect the device.")
                    }
                    if (expectedFastbootSerial != null) {
                        if (fastboot?.isConnected != true) context.failOperation(text(R.string.error_no_fastboot))
                        val actual = FastbootSerialSelection.actual(currentUsbSerial()) { fastboot.getVar("serialno") }
                        if (!FastbootSerialSelection.matches(expectedFastbootSerial, actual)) {
                            context.failOperation(text(R.string.rev19_fastboot_serial_mismatch,
                                expectedFastbootSerial, actual ?: text(R.string.device_bool_unknown)))
                        }
                    }
                    coroutineContext.ensureActive()
                    context.block()
                } catch (abort: OperationAbort) {
                    result.set(abort.outcome)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val message = error.message ?: error.javaClass.simpleName
                    result.set(OperationOutcome.Failed(message))
                    log(text(R.string.operation_error, message))
                }
            }
            activeServiceOperation = task
            operationJob = task.job
            synchronized(checkpointLock) { persistedOperation = pending }
            if (heavy) postOperationProgress(OperationProgress(title, -1, text))
            _operationActive.postValue(true)
            diagnosticSessionTracker.recordOperationStarted(title)
            persistSessionSummary()
            try {
                recoveryStore.writeOperation(pending)
                FlashOperationService.submit(getApplication(), task)
            } catch (error: Exception) {
                task.rejectStartup(text(R.string.rev20_state_save_failed, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    private fun finishServiceOperation(
        task: ServiceOwnedOperation,
        generation: Long,
        reported: OperationOutcome,
        cause: Throwable?
    ) {
        synchronized(operationLaunchLock) {
            if (generation != operationGeneration.get()) {
                FlashOperationService.complete(task.id)
                return
            }
            val outcome = when {
                task.startupFailure != null -> OperationOutcome.Failed(requireNotNull(task.startupFailure))
                task.serviceLost -> OperationOutcome.Interrupted(text(R.string.rev20_service_lost))
                cause is CancellationException -> OperationOutcome.Cancelled(text(R.string.operation_cancelled))
                cause != null -> OperationOutcome.Failed(cause.message ?: cause.javaClass.simpleName)
                else -> reported
            }
            val kind = when (outcome) {
                OperationOutcome.Success -> OperationOutcomeKind.SUCCESS
                is OperationOutcome.Failed -> OperationOutcomeKind.FAILED
                is OperationOutcome.Cancelled -> OperationOutcomeKind.CANCELLED
                is OperationOutcome.VerifyPending -> OperationOutcomeKind.VERIFY_PENDING
                is OperationOutcome.Interrupted -> OperationOutcomeKind.INTERRUPTED
            }
            val message = when (outcome) {
                OperationOutcome.Success -> if (task.tracksProgress) operationProgressSnapshot?.detail.orEmpty() else text(R.string.layout_operation_center_completed)
                is OperationOutcome.Failed -> outcome.message
                is OperationOutcome.Cancelled -> outcome.message
                is OperationOutcome.VerifyPending -> outcome.message
                is OperationOutcome.Interrupted -> outcome.message
            }
            val successful = kind == OperationOutcomeKind.SUCCESS
            val percent = if (successful) 100 else lastPersistedOperation(task.id)?.percent ?: -1
            releaseOperationWakeLock(logRelease = true)
            synchronized(checkpointLock) {
                val record = persistedOperation?.takeIf { it.id == task.id }
                if (record != null) {
                    val finished = record.copy(detail = message, percent = percent, phase = PersistedOperationPhase.valueOf(kind.name))
                    persistedOperation = finished
                    runCatching { recoveryStore.writeOperation(finished) }
                        .onFailure { log(text(R.string.rev20_state_save_failed, it.message ?: it.javaClass.simpleName)) }
                }
            }
            postOperationProgress(OperationProgress(task.title, percent, message, finished = true, success = successful, outcome = kind))
            if (kind == OperationOutcomeKind.FAILED || kind == OperationOutcomeKind.INTERRUPTED) log("❌ $message")
            diagnosticSessionTracker.recordOperationFinished(task.title, kind.name)
            persistSessionSummary()
            _operationActive.postValue(false)
            FlashOperationService.complete(task.id)
            activeServiceOperation = null
            operationJob = null
        }
    }

    private fun acquireOperationWakeLock() {
        try {
            val wl = getApplication<Application>()
                .getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NekoFlash:FlashOperation")
                .apply { setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS) }
            operationWakeLock = wl
            log(text(R.string.wake_lock_acquired))
        } catch (e: Exception) {
            operationWakeLock = null
            log(text(R.string.wake_lock_error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun releaseOperationWakeLock(logRelease: Boolean) {
        val wl = operationWakeLock ?: return
        operationWakeLock = null
        try {
            if (wl.isHeld) wl.release()
            if (logRelease) log(text(R.string.wake_lock_released))
        } catch (e: Exception) {
            if (logRelease) log(text(R.string.wake_lock_release_error, e.message ?: e.javaClass.simpleName))
        }
    }

    fun cancelActiveOperation() {
        synchronized(operationLaunchLock) {
            val task = activeServiceOperation
            if (task == null || task.job.isCompleted) {
                log(text(R.string.operation_cancelled))
                return
            }
            task.cancel("Operation cancellation requested")
            if (!task.job.isCompleted) {
                postOperationProgress(operationProgressSnapshot?.copy(
                    detail = text(R.string.rev20_cancelling_cleanup)
                ))
            }
        }
    }

    // ─── ОТКЛЮЧЕНИЕ ──────────────────────────────────────────────────────────

    fun disconnectCurrent() {
        synchronized(operationLaunchLock) {
            val generation = connectionGeneration.incrementAndGet()
            val transitionJob = transportScope.launch(start = CoroutineStart.LAZY) {
                transportTransitionMutex.withLock {
                    val clean = shutdownCurrentTransportsSafely("disconnect generation=$generation")
                    if (clean) publishDisconnectedState("disconnect")
                }
            }
            connectionJob = transitionJob
            transitionJob.start()
        }
    }

    override fun onCleared() {
        mainHandler.removeCallbacksAndMessages(null)
        flushDiagnostics("VIEWMODEL_CLEARED", terminal = true)
        viewModelCleared.set(true)
        synchronized(operationLaunchLock) {
            val generation = connectionGeneration.incrementAndGet()
            val transitionJob = transportScope.launch(start = CoroutineStart.LAZY) {
                transportTransitionMutex.withLock {
                    val clean = shutdownCurrentTransportsSafely("ViewModel cleared generation=$generation")
                    if (clean) publishDisconnectedState("ViewModel cleared")
                }
                transportScope.cancel()
            }
            connectionJob = transitionJob
            transitionJob.start()
        }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val TRANSPORT_SHUTDOWN_TIMEOUT_MS = 100_000L
        private const val TRANSPORT_IDLE_POLL_MS = 10L
        private const val MI_UNLOCK_VERIFY_TIMEOUT_MS = 24L * 60L * 60L * 1000L
        private const val SIDELOAD_VERIFY_TIMEOUT_MS = 60L * 60L * 1000L
        private const val MI_UNLOCK_VERIFY_PREFS = "mi_unlock_verify"
        private const val SIDELOAD_VERIFY_PREFS = "sideload_verify"
        private const val MI_UNLOCK_VERIFY_PRODUCT = "product"
        private const val MI_UNLOCK_VERIFY_SERIAL = "serial"
        private const val MI_UNLOCK_VERIFY_EXPECTED_UNLOCKED = "expected_unlocked"
        private const val MI_UNLOCK_VERIFY_OPERATION_LABEL = "operation_label"
        private const val MI_UNLOCK_VERIFY_CREATED_AT = "created_at"
        private const val SIDELOAD_VERIFY_PACKAGE = "package_name"
        private const val SIDELOAD_VERIFY_PACKAGE_SIZE = "package_size"
        private const val SIDELOAD_VERIFY_ATTEMPT_ID = "attempt_id"
        private const val SIDELOAD_VERIFY_SERIAL = "serial"
        private const val SIDELOAD_VERIFY_BOOT_ID = "boot_id"
        private const val SIDELOAD_VERIFY_LOG_BYTES = "log_bytes"
        private const val SIDELOAD_VERIFY_LOG_HASH = "log_hash"
        private const val SIDELOAD_VERIFY_TRANSFER_FINISHED = "transfer_finished"
        private const val SIDELOAD_VERIFY_CREATED_AT = "created_at"
    }
}
