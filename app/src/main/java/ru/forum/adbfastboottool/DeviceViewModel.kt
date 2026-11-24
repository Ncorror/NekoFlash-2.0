package ru.forum.adbfastboottool

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Handler
import android.os.Environment
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Lifecycle-владелец сеанса подключения и пользовательских операций NekoFlash.
 *
 * ViewModel сериализует подключение ADB/Fastboot, хранит наблюдаемое состояние экранов
 * и публикует прогресс. Mutation-команды передаются напрямую реальному protocol-слою;
 * ViewModel не вводит отдельные host-side разрешения поверх bootloader/ADB.
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    enum class ConnectionState { NONE, CONNECTING, FASTBOOT, ADB, ERROR }

    enum class OperationStepStatus { PENDING, RUNNING, OK, FAILED, SKIPPED, INFO }

    enum class OperationOutcomeKind { SUCCESS, FAILED, CANCELLED, VERIFY_PENDING, INTERRUPTED }

    internal enum class LocalTerminalAdmission { STARTED, LOCAL_BUSY, USB_BUSY, WORKSPACE_BUSY }

    internal data class LocalCopyProgress(val copiedBytes: Long, val totalBytes: Long)

    internal class LocalOverwriteRequest(val file: File)
    internal class LocalDeleteRequest(val file: File)
    internal class LocalScriptConfirmationRequest(val message: String)

    sealed class OperationOutcome {
        object Success : OperationOutcome()
        data class Failed(val message: String) : OperationOutcome()
        data class Cancelled(val message: String) : OperationOutcome()
        data class VerifyPending(val message: String) : OperationOutcome()
        data class Interrupted(val message: String) : OperationOutcome()
    }

    private class OperationAbort(val outcome: OperationOutcome) :
        RuntimeException(null, null, false, false)

    private class OperationContext(private val recordConfirmedSuccess: (String) -> Unit) {
        fun confirmSuccess(message: String) = recordConfirmedSuccess(message)

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

    // Per-connection informational metadata. Never persist it across USB generations.
    private val _adbAndroidRelease = MutableLiveData<String?>(null)
    val adbAndroidRelease: LiveData<String?> = _adbAndroidRelease

    private val _operationActive   = MutableLiveData(false)
    val operationActive: LiveData<Boolean> = _operationActive

    internal val workspaceImport = WorkspaceImportController(application) { log(it) }
    private val terminalPreferences = application.getSharedPreferences("local_terminal", Context.MODE_PRIVATE)
    internal val localTerminalHome = File(application.filesDir, "HOME")
    private val publicNekoWorkspace = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "NekoFlash",
    )
    internal val localShell = LocalShell(
        availableSpace = { StatFs(it.absolutePath).availableBytes },
        initialHome = localTerminalHome,
        initialDeviceBinariesEnabled = terminalPreferences.getBoolean("device_binaries_enabled", false),
        onDeviceBinariesChanged = { enabled ->
            terminalPreferences.edit { putBoolean("device_binaries_enabled", enabled) }
            refreshTerminalWelcome()
        },
    )
    private val _localTerminalRunning = MutableLiveData(false)
    val localTerminalRunning: LiveData<Boolean> = _localTerminalRunning
    private val _localTerminalCopyProgress = MutableLiveData<LocalCopyProgress?>(null)
    internal val localTerminalCopyProgress: LiveData<LocalCopyProgress?> = _localTerminalCopyProgress
    private val _localTerminalOverwrite = MutableLiveData<LocalOverwriteRequest?>(null)
    internal val localTerminalOverwrite: LiveData<LocalOverwriteRequest?> = _localTerminalOverwrite
    private val _localTerminalDelete = MutableLiveData<LocalDeleteRequest?>(null)
    internal val localTerminalDelete: LiveData<LocalDeleteRequest?> = _localTerminalDelete
    private val _localScriptConfirmation = MutableLiveData<LocalScriptConfirmationRequest?>()
    internal val localScriptConfirmation: LiveData<LocalScriptConfirmationRequest?> = _localScriptConfirmation

    internal fun importWorkspaceFile(uri: android.net.Uri, workspace: File): Boolean = synchronized(operationLaunchLock) {
        if (viewModelCleared.get() || localTerminalOperation != null || workspaceImport.isBusy || hasUnfinishedUsbOperation()) {
            false
        } else {
            workspaceImport.start(uri, workspace)
        }
    }

    /** Share the launch lock so workspace cleanup cannot race a new operation. */
    fun runWorkspaceMaintenanceIfIdle(action: () -> Unit): Boolean = synchronized(operationLaunchLock) {
        if (workspaceImport.isBusy || localTerminalOperation != null || connectionJob?.isCompleted == false ||
            hasUnfinishedUsbOperation()
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
    private data class SparseProgress(val label: String, val before: Long, val bytes: Long, val total: Long)
    @Volatile private var sparseProgress: SparseProgress? = null

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

    internal data class MiUnlockRejection(
        val accountId: String,
        val zone: String,
        val transportSessionId: String?,
        val product: String,
        val code: Int,
        val serverText: String?,
        val waitHours: Int?
    )

    private val _miUnlockRejection = MutableLiveData<MiUnlockRejection?>(null)
    internal val miUnlockRejection: LiveData<MiUnlockRejection?> = _miUnlockRejection

    private val flashDraftLock = Any()
    private var flashDraftSnapshot: FlashOperationDraft =
        runCatching { recoveryStore.readDraft() }.getOrNull() ?: FlashOperationDraft()
    private val _flashOperationDraft = MutableLiveData(flashDraftSnapshot)
    val flashOperationDraft: LiveData<FlashOperationDraft> = _flashOperationDraft

    private val quickSourceLock = Any()
    private var quickSourceGeneration = 0L
    @Volatile private var quickSourceSnapshot = QuickFlashSourceState()
    private val _quickFlashSource = MutableLiveData(quickSourceSnapshot)
    internal val quickFlashSource: LiveData<QuickFlashSourceState> = _quickFlashSource
    private var queueSourceGeneration = 0L
    private val pendingQueueSources = mutableMapOf<String, Long>()
    private val _queueSourceState = MutableLiveData(QueueSourceState())
    internal val queueSourceState: LiveData<QueueSourceState> = _queueSourceState

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
        val transferFinished: Boolean,
        val sourceRecovery: SideloadRecoveryTransitionPolicy.Identity?,
        val recoveryTransitionObserved: Boolean
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

    private var compactPublishScheduled = false
    private val publishCompact = Runnable {
        synchronized(logLock) {
            compactPublishScheduled = false
            _logLines.value = lines.toList()
        }
    }
    private fun scheduleCompactPublishLocked() {
        if (!compactPublishScheduled) {
            compactPublishScheduled = true
            mainHandler.postDelayed(publishCompact, CompactLogRenderPolicy.RENDER_DEBOUNCE_MS)
        }
    }
    private val _logLines = MutableLiveData(initialLines)
    val logLines: LiveData<List<String>> = _logLines

    private val transcript = TerminalTranscript().apply {
        TerminalWelcomeMessage.build(application, deviceBinariesEnabled = localShell.isDeviceBinariesEnabled).forEach(::append)
    }
    private val _terminalLines = MutableLiveData(transcript.snapshot())
    internal val terminalLines: LiveData<List<String>> = _terminalLines
    private val terminalSubmission = ThreadLocal<Boolean>()
    @Volatile private var terminalOperationId: String? = null
    internal val isTerminalOperation: Boolean get() = terminalOperationId != null
    private var terminalPublishScheduled = false
    private val publishTerminal = Runnable {
        synchronized(logLock) {
            terminalPublishScheduled = false
            _terminalLines.value = transcript.snapshot()
        }
    }

    internal fun <T> withTerminalCommand(action: () -> T): T {
        val previous = terminalSubmission.get()
        terminalSubmission.set(true)
        return try { action() } finally { terminalSubmission.set(previous) }
    }

    private fun appendTerminalLocked(message: String) {
        transcript.append(message)
        if (!terminalPublishScheduled) {
            terminalPublishScheduled = true
            mainHandler.postDelayed(publishTerminal, CompactLogRenderPolicy.RENDER_DEBOUNCE_MS)
        }
    }
    private fun refreshTerminalWelcome() {
        synchronized(logLock) {
            val previous = transcript.snapshot()
            val welcomeCount = previous.takeWhile(TerminalWelcomeMessage::isWelcomeLine).size
            transcript.clear()
            TerminalWelcomeMessage.build(
                getApplication<Application>(),
                deviceBinariesEnabled = localShell.isDeviceBinariesEnabled,
            ).forEach(transcript::append)
            previous.drop(welcomeCount).forEach(transcript::append)
            if (!terminalPublishScheduled) {
                terminalPublishScheduled = true
                mainHandler.postDelayed(publishTerminal, CompactLogRenderPolicy.RENDER_DEBOUNCE_MS)
            }
        }
    }
    internal fun terminalSnapshot(): List<String> = synchronized(logLock) { transcript.snapshot() }
    internal fun clearTerminal() {
        synchronized(logLock) {
            transcript.clear()
            appendTerminalLocked(text(R.string.log_cleared))
        }
    }

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
    private var lastSummaryPersistAtMs: Long = 0L
    private var workspaceRoot: File? = null
    private var lastUsbSessionSnapshot: UsbSessionSnapshot? = null
    private var connectedUsbManager: UsbManager? = null
    private var activeTransportSessionId: String? = null
    private val transportSessionSequence = AtomicLong(0L)
    private val adbKeyDir: File = File(application.filesDir, "adbkeys")
    private val wirelessAdbKeys = AdbKeyStore(File(application.filesDir, "adb-wifi-keys")) { logFileOnly(it) }

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
    private data class LocalTerminalOperation(val job: Job, val cancellation: ImportCancellation)
    private var localTerminalOperation: LocalTerminalOperation? = null
    private val localTerminalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class LocalOverwriteConfirmation(val request: LocalOverwriteRequest, val answer: CompletableDeferred<Boolean>)
    private var localOverwriteConfirmation: LocalOverwriteConfirmation? = null
    private data class LocalDeleteConfirmation(val request: LocalDeleteRequest, val answer: CompletableDeferred<Boolean>)
    private var localDeleteConfirmation: LocalDeleteConfirmation? = null
    private data class LocalScriptConfirmation(
        val request: LocalScriptConfirmationRequest,
        val answer: CompletableDeferred<Boolean>,
    )
    private var localScriptConfirmationState: LocalScriptConfirmation? = null
    @Volatile private var localScriptFastbootActive = false
    @Volatile private var localScriptExecutionActive = false
    @Volatile private var localScriptServiceOwned = false
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


    internal fun refreshLocalTerminalHome() {
        if (!PermissionGate.hasStorage(getApplication())) return
        runCatching { localShell.configureHome(localTerminalHome, publicNekoWorkspace) }
            .onFailure { logLocalTerminal("⚠️ Shared Download link unavailable: ${it.message ?: it.javaClass.simpleName}") }
    }

    init {
        refreshLocalTerminalHome()
        DiagnosticCrashMarker.install(File(application.filesDir, "diagnostics"), BuildConfig.BUILD_ID)
        restoreOperationState()
        runCatching { recoveryStore.readQuickSource() }
            .onSuccess { it?.let(::checkQuickFlashSource) }
            .onFailure { log(text(R.string.rev24_source_save_failed, it.message ?: it.javaClass.simpleName)) }
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

    private fun logInternal(message: String, declaredLevel: DiagnosticLogPolicy.Level?, protocol: Boolean = false) {
        synchronized(logLock) {
            TerminalTranscript.select(message, declaredLevel, terminalSubmission.get() == true,
                protocol, isTerminalOperation)?.let(::appendTerminalLocked)
            // All emitted events, including identical consecutive messages, must
            // reach the on-disk beta journal. Only the live UI is bounded.
            if (message.trim().startsWith("💡")) {
                lines.removeAll { it.trim().startsWith("💡") }
            }
            appendCompactMessageLocked(message, declaredLevel)
        }
    }

    fun clearLog() {
        synchronized(logLock) {
            lines.clear()
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
        scheduleCompactPublishLocked()
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
        val message = "❌ Diagnostic storage error: ${error.javaClass.simpleName}. Check free space; saved logs may be incomplete."
        lines.add(message)
        appendTerminalLocked(message)
        scheduleCompactPublishLocked()
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
            _adbAndroidRelease.postValue(null)
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

    internal suspend fun pairWirelessAdb(host: String, port: Int, code: String) = withContext(Dispatchers.IO) {
        AdbWirelessClient(wirelessAdbKeys).pair(host, port, code)
    }

    internal fun connectWirelessAdb(host: String, port: Int) {
        if (transportRestartRequired.get()) {
            log(text(R.string.adb_wifi_restart_transport))
            _connectionState.postValue(ConnectionState.ERROR)
            return
        }
        synchronized(operationLaunchLock) {
            if (viewModelCleared.get()) return
            val generation = connectionGeneration.incrementAndGet()
            val endpoint = if (':' in host) "[$host]:$port" else "$host:$port"
            pendingUsbTargetKey = null
            _adbAndroidRelease.postValue(null)
            _fastbootDiagnostics.postValue(null)
            _fastbootPartitionInventory.postValue(null)
            _adbPeerMode.postValue(null)
            _connectionState.postValue(ConnectionState.CONNECTING)

            val transitionJob = transportScope.launch(start = CoroutineStart.LAZY) {
                transportTransitionMutex.withLock {
                    if (viewModelCleared.get() || generation != connectionGeneration.get()) return@withLock
                    if (!shutdownCurrentTransportsSafely("new Wi-Fi ADB generation=$generation")) return@withLock
                    if (viewModelCleared.get() || generation != connectionGeneration.get()) return@withLock

                    publishDisconnectedState("switch to Wi-Fi ADB")
                    connectedDeviceInfo = "Mode: ADB | Device: ${text(R.string.adb_wifi_endpoint, endpoint)}"
                    val sessionId = "wifi-adb-${transportSessionSequence.incrementAndGet()}"
                    activeTransportSessionId = sessionId
                    _transportSessionId.postValue(sessionId)
                    diagnosticSessionTracker.recordTransportSession(sessionId, "ADB", connectedDeviceInfo)
                    _connectionInfo.postValue(connectedDeviceInfo)
                    _connectionState.postValue(ConnectionState.CONNECTING)
                    connectAdbLocked(
                        AdbProtocol(
                            keyStore = wirelessAdbKeys,
                            host = host,
                            port = port,
                            rawShellLogsDir = File(workspaceRoot ?: getApplication<Application>().filesDir, "logs"),
                            onLog = { msg -> logInternal(msg, declaredLevel = null, protocol = true) },
                            onProgress = ::postAdbProgress
                        ),
                        generation
                    )
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
                onLog = { msg -> logInternal(msg, declaredLevel = null, protocol = true) },
                onLogVerbose = { msg -> logFileOnly(msg) },
                onProgress = { percent, detail ->
                    val currentProgress = operationProgressSnapshot
                    val sparse = sparseProgress
                    postOperationProgress(
                        OperationProgress(
                            title = currentProgress?.title ?: text(R.string.flash_progress_writing),
                            percent = if (sparse != null && percent >= 0)
                                ((sparse.before.toDouble() + sparse.bytes.toDouble() * percent / 100) * 100 / sparse.total).toInt().coerceIn(0, 100)
                                else percent,
                            detail = sparse?.let { "${it.label} · $detail" } ?: detail
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
                // The exact binary shell streams live beside the public
                // compact/trace logs, so a tester can include them in a ZIP.
                // No rotation, sanitization or automatic pruning of raw bytes.
                rawShellLogsDir = File(workspaceRoot ?: getApplication<Application>().filesDir, "logs"),
                onLog = { msg -> logInternal(msg, declaredLevel = null, protocol = true) },
                onProgress = ::postAdbProgress,
                preferredInterfaceIndex = candidate.interfaceIndex
            )
            connectAdbLocked(proto, generation)
        }
    }

    private fun postAdbProgress(percent: Int, detail: String) {
        val currentProgress = operationProgressSnapshot
        postOperationProgress(
            OperationProgress(
                title = currentProgress?.title ?: text(R.string.flash_progress_writing),
                percent = percent,
                detail = detail
            )
        )
    }

    private fun connectAdbLocked(proto: AdbProtocol, generation: Long) {
        proto.onTransportFailure = { code, message ->
            synchronized(operationLaunchLock) {
                if (!viewModelCleared.get() && generation == connectionGeneration.get()) {
                    diagnosticSessionTracker.recordTermination("ADB_READER_${code.name}")
                    _adbAndroidRelease.postValue(null)
                    diagnosticSessionTracker.recordMilestone("adb.reader.failure", 0L)
                    persistSessionSummary()
                    _connectionState.postValue(ConnectionState.ERROR)
                    log("⛔ ADB transport stopped [${code.name}]: $message. Auto-retry is forbidden until manual reconnection.")
                }
            }
        }
        if (proto.isWireless) {
            proto.onExpectedRebootDisconnect = {
                synchronized(operationLaunchLock) {
                    if (!viewModelCleared.get() && generation == connectionGeneration.get() && adbProtocol === proto) {
                        adbProtocol = null
                        // Reboot acceptance is resolved by the operation; only the connection ended.
                        publishDisconnectedState("expected Wi-Fi ADB reboot")
                    }
                }
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

            verifyPendingSideloadIfReady(proto)
            // Recovery installers can boot straight back into SIDELOAD, not RECOVERY.
            // Record the switch as a postcondition; never infer ZIP install SUCCESS.
            observePendingSideloadRecoveryTransition(proto)
            if (proto.peerMode == AdbProtocol.PeerMode.RECOVERY) {
                val serial = currentUsbSerial()
                val boundary = if (serial != null) proto.captureRecoveryLogBoundary() else null
                preparedRecoveryLog = if (serial != null && boundary != null) {
                    PreparedRecoveryLog(serial, boundary, System.currentTimeMillis())
                } else null
            } else if (proto.peerMode != AdbProtocol.PeerMode.SIDELOAD) {
                preparedRecoveryLog = null
            }
            // ADB stream consumers share the same packet queue. Finish this optional
            // read within the connection job before exposing the transport to commands.
            val release = if (proto.peerMode == AdbProtocol.PeerMode.DEVICE) {
                runCatching {
                    proto.captureShellCommand("getprop ro.build.version.release", readBudgetMs = 3_000)
                }.getOrNull()?.let { AndroidReleaseMetadataPolicy.parse(it.stdout, it.success, it.exitCode) }
            } else null
            synchronized(operationLaunchLock) {
                if (viewModelCleared.get() || generation != connectionGeneration.get() || !proto.isConnected) return
                adbProtocol = proto
                published = true
                _fastbootDiagnostics.postValue(null)
                _fastbootPartitionInventory.postValue(null)
                _adbPeerMode.postValue(proto.peerMode)
                _adbAndroidRelease.postValue(release)
                diagnosticSessionTracker.recordConnection("ADB", connectedDeviceInfo)
                persistSessionSummary()
                logConnectionStatus()
                _connectionState.postValue(ConnectionState.ADB)
            }
        } finally {
            if (!published || generation != connectionGeneration.get() || viewModelCleared.get()) {
                if (adbProtocol === proto) adbProtocol = null
                proto.disconnect()
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
            // Closing the Wi-Fi socket wakes a blocked network read or write. USB still drains below.
            adbProtocol?.takeIf { it.isWireless }?.disconnect()

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
        _adbAndroidRelease.postValue(null)
        _fastbootDiagnostics.postValue(null)
        _fastbootPartitionInventory.postValue(null)
        _adbPeerMode.postValue(null)
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
        persistSessionSummary()
    }
    fun currentDiagnosticSessionSummary(): DiagnosticSessionTracker.Snapshot = synchronized(logLock) {
        diagnosticSessionTracker.snapshot()
    }
    fun logSnapshot(): List<String> = synchronized(logLock) {
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
        startOperation(text(R.string.notif_fastboot_diagnostics), text(R.string.notif_updating_device), heavy = false, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
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
     * Шаги: чтение product+token с устройства → nonce → clear → новый nonce → ahaUnlock →
     * staging encryptData + oem unlock.
     */
    fun runMiUnlock(
        auth: MiAccountClient.AuthResult,
        onAuthExpired: () -> Unit = {}
    ) {
        startOperation(text(R.string.notif_fastboot_command), "Mi Unlock", heavy = true, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
            _miUnlockRejection.postValue(null)
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
            val transportSessionId = currentTransportSessionId()

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
                log("🌐 Requesting a nonce and checking whether unlock erases data...")
                val clearInfo = client.checkClear(product)
                if (clearInfo.notice.isNotEmpty()) log("ℹ️ ${clearInfo.notice}")
                log(when (clearInfo.clearsData) {
                    true -> "⚠️ Unlocking WILL ERASE device data"
                    false -> "ℹ️ Xiaomi reports that unlocking will not erase device data"
                    null -> "⚠️ Xiaomi did not report whether unlocking will erase device data"
                })

                log("🌐 Requesting a new nonce for the Xiaomi unlock request...")
                val encryptDataHex = client.requestUnlock(product, deviceToken)
                log("✅ Server returned unlock data")

                val bytes = hexToBytes(encryptDataHex)
                val file = File(getApplication<Application>().filesDir, "encryptData")
                file.outputStream().use { it.write(bytes) }
                val result = try {
                    proto.stageAndOemUnlockDetailed(file) {
                        confirmSuccess(text(R.string.mi_unlock_success_detail))
                    }
                } finally {
                    runCatching { file.delete() }
                }

                when (result) {
                    MiUnlockDeviceOperation.Result.Confirmed -> Unit
                    is MiUnlockDeviceOperation.Result.Failed ->
                        failOperation(text(R.string.mi_unlock_device_failed, result.stage.name, result.reason))
                    is MiUnlockDeviceOperation.Result.Unconfirmed -> {
                        // Optional later Fastboot evidence may resolve a lost reply. A received
                        // final OKAY never creates this marker or requires another Fastboot boot.
                        persistPendingUnlockVerification(product, serial)
                        verificationPending(text(R.string.mi_unlock_result_unconfirmed, result.reason))
                    }
                }
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
                e.waitHours?.let { log("ℹ️ Xiaomi waiting period: $it hours at the time of the response") }
                _miUnlockRejection.postValue(
                    MiUnlockRejection(auth.userId, auth.dataCenterZone, transportSessionId, product,
                        e.code, e.serverText, e.waitHours)
                )
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

    internal fun logLocalTerminal(message: String) = log("> [Local] $message")

    private fun executeScriptFastbootCommand(
        arguments: List<String>,
        workingDirectory: File,
    ): FastbootShellBridge.Result {
        if (arguments.isEmpty()) return FastbootShellBridge.Result(1, listOf("❌ fastboot: missing command"))
        val proto = fastbootProtocol
            ?: return FastbootShellBridge.Result(1, listOf("❌ No Fastboot USB session. Connect the device in Fastboot mode first."))
        if (!proto.isConnected) return FastbootShellBridge.Result(1, listOf("❌ Fastboot USB session is not connected"))

        var index = 0
        var serial: String? = null
        var wipe = false
        var disableVerity = false
        var disableVerification = false
        var slot: String? = null
        var setActiveOption: String? = null
        val values = arguments.toMutableList()
        while (index < values.size && values[index].startsWith('-')) {
            when {
                values[index] == "-s" -> {
                    serial = values.getOrNull(index + 1)
                        ?: return FastbootShellBridge.Result(2, listOf("❌ fastboot -s requires a serial"))
                    index += 2
                }
                values[index] == "-w" -> { wipe = true; index++ }
                values[index] == "-S" || values[index] == "-i" || values[index] == "-p" -> {
                    return FastbootShellBridge.Result(2, listOf("❌ ${values[index]} is not implemented by NekoFlash's USB Fastboot transport; refusing to ignore it"))
                }
                values[index] == "--disable-verity" -> { disableVerity = true; index++ }
                values[index] == "--disable-verification" -> { disableVerification = true; index++ }
                values[index] == "--slot" -> {
                    slot = values.getOrNull(index + 1)
                        ?: return FastbootShellBridge.Result(2, listOf("❌ --slot requires a or b"))
                    index += 2
                }
                values[index].startsWith("--slot=") -> {
                    slot = values[index].substringAfter('=')
                    index++
                }
                values[index].startsWith("--set-active=") -> {
                    setActiveOption = values[index].substringAfter('=')
                    index++
                }
                values[index] in setOf("--skip-reboot", "--verbose") -> index++
                values[index] == "--force" -> {
                    return FastbootShellBridge.Result(2, listOf("❌ ${values[index]} is not implemented; refusing to silently change Fastboot semantics"))
                }
                else -> return FastbootShellBridge.Result(2, listOf("❌ Unsupported Fastboot option: ${values[index]}"))
            }
        }
        val command = if (setActiveOption != null) listOf("set_active", setActiveOption!!) else values.drop(index)
        if (command.isEmpty()) return FastbootShellBridge.Result(2, listOf("❌ fastboot: missing command"))
        val verb = command[0].lowercase(Locale.US)

        if (serial != null) {
            if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
            val actual = FastbootSerialSelection.actual(currentUsbSerial()) { proto.getVar("serialno") }
            if (!FastbootSerialSelection.matches(serial, actual)) {
                return FastbootShellBridge.Result(1, listOf("❌ Serial mismatch: requested=$serial actual=${actual ?: "unknown"}"))
            }
        }

        val subcommand = command.getOrNull(1)?.lowercase(Locale.US).orEmpty()
        val lockOrWipe = wipe || verb == "erase" ||
            (verb == "flashing" && subcommand !in setOf("get_unlock_ability", "get-bootloader-info")) ||
            (verb == "oem" && subcommand !in setOf("device-info", "getvar"))
        if (lockOrWipe) {
            val summary = when {
                wipe -> "The ROM script requested -w. This erases userdata and cache."
                verb == "erase" -> "The ROM script requested erase ${command.getOrNull(1) ?: "<missing partition>"}."
                else -> "The ROM script requested ${command.joinToString(" ")}. This changes the bootloader lock state and may permanently prevent booting an unsigned/custom ROM."
            }
            val approved = runCatching {
                runBlocking {
                    withTimeoutOrNull(120_000L) { requestLocalScriptConfirmation(summary) } ?: false
                }
            }.getOrDefault(false)
            if (!approved || !localScriptExecutionActive) {
                return FastbootShellBridge.Result(130, listOf("⚠️ User declined or cancelled the destructive Fastboot operation"))
            }
        }

        localScriptFastbootActive = true
        try {
            if (wipe) {
                for (partition in listOf("userdata", "cache")) {
                    if (!proto.beginOperation() || !proto.sendCommand("erase:$partition")) {
                        return FastbootShellBridge.Result(1, listOf("❌ Wipe failed at erase:$partition"))
                    }
                }
            }
            return when (verb) {
                "devices" -> {
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    val actual = currentUsbSerial() ?: proto.getVar("serialno") ?: "unknown"
                    FastbootShellBridge.Result(0, listOf("$actual	fastboot"))
                }
                "getvar" -> {
                    val key = command.getOrNull(1) ?: return FastbootShellBridge.Result(2, listOf("❌ getvar requires a variable name"))
                    if (!key.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) return FastbootShellBridge.Result(2, listOf("❌ Invalid getvar name: $key"))
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (key.equals("all", ignoreCase = true)) {
                        val snapshot = proto.getVarAll() ?: return FastbootShellBridge.Result(1, listOf("❌ getvar all failed or returned an incomplete response"))
                        if (!snapshot.complete || !snapshot.finalStatus.equals("OKAY", true)) {
                            return FastbootShellBridge.Result(1, listOf("❌ getvar all was incomplete: ${snapshot.finalStatus}"))
                        }
                        FastbootShellBridge.Result(0, snapshot.variables.toSortedMap().map { "(bootloader) ${it.key}: ${it.value}" })
                    } else {
                        val value = proto.getVar(key) ?: return FastbootShellBridge.Result(1, listOf("❌ getvar:$key failed or returned no value"))
                        FastbootShellBridge.Result(0, listOf("(bootloader) $key: $value"))
                    }
                }
                "flash" -> {
                    val partition = command.getOrNull(1) ?: return FastbootShellBridge.Result(2, listOf("❌ flash requires PARTITION FILE"))
                    if (!partition.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) return FastbootShellBridge.Result(2, listOf("❌ Invalid partition name: $partition"))
                    val imagePath = command.getOrNull(2) ?: return FastbootShellBridge.Result(2, listOf("❌ flash requires PARTITION FILE"))
                    if (command.size != 3) return FastbootShellBridge.Result(2, listOf("❌ Unsupported extra arguments for fastboot flash"))
                    val requestedSlot = slot?.removePrefix("_")?.lowercase(Locale.US)
                    val actualSlot = when (requestedSlot) {
                        null -> null
                        "a", "b" -> requestedSlot
                        "other" -> {
                            if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready while resolving --slot=other"))
                            val currentSlot = proto.getVar("current-slot")?.trim()?.removePrefix("_")?.lowercase(Locale.US)
                            if (currentSlot !in setOf("a", "b")) return FastbootShellBridge.Result(1, listOf("❌ Cannot resolve --slot=other: bootloader did not report current-slot a/b"))
                            if (currentSlot == "a") "b" else "a"
                        }
                        "all" -> "all"
                        else -> return FastbootShellBridge.Result(2, listOf("❌ Invalid --slot value: $requestedSlot"))
                    }
                    val image = File(imagePath).let { if (it.isAbsolute) it else File(workingDirectory, imagePath) }.canonicalFile
                    if (!image.isFile || !image.canRead()) return FastbootShellBridge.Result(1, listOf("❌ Image file is missing or unreadable: ${image.path}"))
                    val targetPartitions = when (actualSlot) {
                        "all" -> {
                            if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready while resolving --slot=all"))
                            val hasSlots = proto.getVar("has-slot:$partition")?.trim()?.equals("yes", true) == true
                            if (hasSlots) listOf("${partition}_a", "${partition}_b") else listOf(partition)
                        }
                        null -> listOf(partition)
                        else -> if (partition.endsWith("_a") || partition.endsWith("_b")) {
                            if (partition.endsWith("_$actualSlot")) listOf(partition)
                            else return FastbootShellBridge.Result(2, listOf("❌ Partition $partition conflicts with --slot=$actualSlot"))
                        } else listOf("${partition}_$actualSlot")
                    }
                    val lines = mutableListOf<String>()
                    for (targetPartition in targetPartitions) {
                        if (!proto.beginOperation()) return FastbootShellBridge.Result(1, lines + "❌ Fastboot session is not ready before flash:$targetPartition")
                        val patchedVbmeta = if (
                            (disableVerity || disableVerification) &&
                            targetPartition in setOf("vbmeta", "vbmeta_a", "vbmeta_b")
                        ) {
                            FastbootVbmeta.createPatchedCopy(
                                source = image,
                                temporaryDirectory = File(getApplication<Application>().cacheDir, "terminal-vbmeta"),
                                disableVerity = disableVerity,
                                disableVerification = disableVerification,
                            )
                        } else null
                        val result = try {
                            proto.flashPartitionDetailed(targetPartition, patchedVbmeta ?: image)
                        } finally {
                            patchedVbmeta?.delete()
                        }
                        if (!result.success) return FastbootShellBridge.Result(1, lines + "❌ ${formatFlashFailure(targetPartition, result)}")
                        val flagNote = if (patchedVbmeta != null) " (AVB flags updated in a private copy)" else ""
                        lines += "✅ flash $targetPartition: ${image.name} (${result.dataBytesTransferred} bytes)$flagNote"
                    }
                    FastbootShellBridge.Result(0, lines)
                }
                "erase" -> {
                    val partition = command.getOrNull(1) ?: return FastbootShellBridge.Result(2, listOf("❌ erase requires a partition"))
                    if (!partition.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) return FastbootShellBridge.Result(2, listOf("❌ Invalid partition name: $partition"))
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (command.size != 2) return FastbootShellBridge.Result(2, listOf("❌ Unsupported extra arguments for erase"))
                    if (proto.sendCommand("erase:$partition")) FastbootShellBridge.Result(0, listOf("✅ erased $partition"))
                    else FastbootShellBridge.Result(1, listOf("❌ erase $partition failed"))
                }
                "set_active", "--set-active" -> {
                    val activeSlot = command.getOrNull(1)?.removePrefix("_")?.lowercase(Locale.US)
                        ?: return FastbootShellBridge.Result(2, listOf("❌ set_active requires a or b"))
                    if (activeSlot !in setOf("a", "b")) return FastbootShellBridge.Result(2, listOf("❌ Invalid active slot: $activeSlot"))
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (proto.sendCommand("set_active:$activeSlot")) FastbootShellBridge.Result(0, listOf("✅ active slot set to $activeSlot"))
                    else FastbootShellBridge.Result(1, listOf("❌ set_active:$activeSlot failed"))
                }
                "reboot", "reboot-bootloader", "reboot-edl", "continue" -> {
                    val wire = when {
                        verb == "reboot" && command.getOrNull(1) == "bootloader" -> "reboot-bootloader"
                        verb == "reboot" && command.getOrNull(1) == "fastboot" -> "reboot:fastboot"
                        verb == "reboot" && command.size > 1 -> return FastbootShellBridge.Result(2, listOf("❌ Unsupported reboot target: ${command.drop(1).joinToString(" ")}"))
                        else -> verb
                    }
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (proto.sendCommand(wire)) FastbootShellBridge.Result(0, listOf("✅ $wire accepted"))
                    else FastbootShellBridge.Result(1, listOf("❌ $wire failed"))
                }
                "flashing", "oem" -> {
                    val request = command.joinToString(" ")
                    if (request.any { it.code !in 32..126 } || request.length > 256) {
                        return FastbootShellBridge.Result(2, listOf("❌ Invalid Fastboot command arguments"))
                    }
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (proto.runTerminalCommand(request)) FastbootShellBridge.Result(0, listOf("✅ $request"))
                    else FastbootShellBridge.Result(1, listOf("❌ $request failed"))
                }
                "boot" -> {
                    val imagePath = command.getOrNull(1) ?: return FastbootShellBridge.Result(2, listOf("❌ boot requires an image file"))
                    val image = File(imagePath).let { if (it.isAbsolute) it else File(workingDirectory, imagePath) }.canonicalFile
                    if (!proto.beginOperation()) return FastbootShellBridge.Result(1, listOf("❌ Fastboot session is not ready"))
                    if (proto.downloadAndRun(image, "boot")) FastbootShellBridge.Result(0, listOf("✅ boot ${image.name}"))
                    else FastbootShellBridge.Result(1, listOf("❌ boot ${image.name} failed"))
                }
                else -> FastbootShellBridge.Result(2, listOf("❌ Unsupported Fastboot command: ${command.joinToString(" ")}"))
            }
        } finally {
            localScriptFastbootActive = false
        }
    }

    internal fun submitLocalTerminalCommand(raw: String, command: String, workspace: File?): LocalTerminalAdmission =
        launchLocalTerminalCommand(localTerminalScope) { cancellation ->
            logLocalTerminal("${localShell.prompt()} $raw")
            val startedAt = SystemClock.elapsedRealtime()
            var lastProgressAt: Long? = null
            fun elapsed(): String {
                val milliseconds = SystemClock.elapsedRealtime() - startedAt
                return if (milliseconds < 1_000L) text(R.string.local_terminal_milliseconds, milliseconds)
                else text(R.string.local_terminal_seconds, milliseconds / 1_000.0)
            }

            try {
                val output = localShell.execute(
                    line = command,
                    workspace = { workspace },
                    cancellation = cancellation,
                    onCopyProgress = { copiedBytes, totalBytes ->
                        val now = SystemClock.elapsedRealtime()
                        val previous = lastProgressAt
                        if (previous == null || now - previous >= 100L || copiedBytes == totalBytes) {
                            _localTerminalCopyProgress.postValue(LocalCopyProgress(copiedBytes, totalBytes))
                            lastProgressAt = now
                        }
                    },
                    confirmOverwrite = ::requestLocalOverwrite,
                    confirmDelete = ::requestLocalDelete,
                    fastbootCommand = ::executeScriptFastbootCommand,
                    onShellOutput = ::logLocalTerminal,
                )
                when {
                    output == null -> logLocalTerminal(text(R.string.local_terminal_workspace_unavailable))
                    output.streamedOutput -> Unit
                    output.lines.isEmpty() -> logLocalTerminal(
                        text(R.string.local_terminal_working_directory, localShell.prompt(output.directory))
                    )
                    else -> output.lines.forEach(::logLocalTerminal)
                }
                if (output != null) {
                    when (output.exitCode) {
                        0 -> logLocalTerminal("✓ ${text(R.string.local_terminal_finished, elapsed())}")
                        130 -> logLocalTerminal("! ${text(R.string.local_terminal_cancelled, elapsed())}")
                        else -> logLocalTerminal(
                            "✕ ${text(R.string.local_terminal_failed, elapsed(), text(R.string.local_terminal_exit_code, output.exitCode))}"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                logLocalTerminal(text(R.string.local_terminal_cancelled, elapsed()))
                throw cancelled
            } catch (error: Exception) {
                logLocalTerminal(text(R.string.local_terminal_failed, elapsed(), error.message ?: error.javaClass.simpleName))
            }
        }

    internal fun submitLocalTerminalScriptCommand(
        raw: String,
        command: String,
        workspace: File?,
    ): LocalTerminalAdmission {
        return synchronized(operationLaunchLock) {
            if (localTerminalOperation != null) return@synchronized LocalTerminalAdmission.LOCAL_BUSY
            if (workspaceImport.isBusy) return@synchronized LocalTerminalAdmission.WORKSPACE_BUSY
            if (viewModelCleared.get() || connectionJob?.isCompleted == false || hasUnfinishedUsbOperation()) {
                return@synchronized LocalTerminalAdmission.USB_BUSY
            }
            localScriptServiceOwned = true
            val started = startOperation(
                title = "Local ROM script",
                text = "Executing $raw through the app-owned Fastboot transport",
                heavy = true,
                prepareFastbootSession = false,
                requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT,
            ) {
                localScriptExecutionActive = true
                val startedAt = SystemClock.elapsedRealtime()
                fun elapsed(): String {
                    val milliseconds = SystemClock.elapsedRealtime() - startedAt
                    return if (milliseconds < 1_000L) text(R.string.local_terminal_milliseconds, milliseconds)
                    else text(R.string.local_terminal_seconds, milliseconds / 1_000.0)
                }
                try {
                    logLocalTerminal("${localShell.prompt()} $raw")
                    var lastProgressAt: Long? = null
                    val output = localShell.execute(
                        line = command,
                        workspace = { workspace },
                        cancellation = ImportCancellation(),
                        onCopyProgress = { copiedBytes, totalBytes ->
                            val now = SystemClock.elapsedRealtime()
                            val previous = lastProgressAt
                            if (previous == null || now - previous >= 100L || copiedBytes == totalBytes) {
                                _localTerminalCopyProgress.postValue(LocalCopyProgress(copiedBytes, totalBytes))
                                lastProgressAt = now
                            }
                        },
                        confirmOverwrite = ::requestLocalOverwrite,
                        confirmDelete = ::requestLocalDelete,
                        fastbootCommand = ::executeScriptFastbootCommand,
                        onShellOutput = ::logLocalTerminal,
                    ) ?: failOperation(text(R.string.local_terminal_workspace_unavailable))

                    when {
                        output.streamedOutput -> Unit
                        output.lines.isEmpty() -> logLocalTerminal(
                            text(R.string.local_terminal_working_directory, localShell.prompt(output.directory))
                        )
                        else -> output.lines.forEach(::logLocalTerminal)
                    }
                    if (output.exitCode != 0) {
                        logLocalTerminal("✕ ${text(R.string.local_terminal_failed, elapsed(), text(R.string.local_terminal_exit_code, output.exitCode))}")
                        failOperation("Local ROM script exited with status ${output.exitCode}")
                    }
                    logLocalTerminal("✓ ${text(R.string.local_terminal_finished, elapsed())}")
                    confirmSuccess("Local ROM script completed successfully (exit 0)")
                } catch (cancelled: CancellationException) {
                    logLocalTerminal("! ${text(R.string.local_terminal_cancelled, elapsed())}")
                    throw cancelled
                } catch (abort: OperationAbort) {
                    throw abort
                } catch (error: Exception) {
                    logLocalTerminal("✕ ${text(R.string.local_terminal_failed, elapsed(), error.message ?: error.javaClass.simpleName)}")
                    throw error
                } finally {
                    localScriptExecutionActive = false
                    localScriptFastbootActive = false
                    localScriptServiceOwned = false
                    _localTerminalCopyProgress.postValue(null)
                }
            } != null
            if (!started) localScriptServiceOwned = false
            if (started) LocalTerminalAdmission.STARTED else LocalTerminalAdmission.USB_BUSY
        }
    }

    private suspend fun requestLocalOverwrite(file: File): Boolean {
        val confirmation = LocalOverwriteConfirmation(LocalOverwriteRequest(file), CompletableDeferred())
        synchronized(operationLaunchLock) {
            localOverwriteConfirmation = confirmation
            _localTerminalOverwrite.postValue(confirmation.request)
        }
        return try {
            confirmation.answer.await()
        } finally {
            synchronized(operationLaunchLock) {
                if (localOverwriteConfirmation === confirmation) {
                    localOverwriteConfirmation = null
                    _localTerminalOverwrite.postValue(null)
                }
            }
        }
    }

    internal fun answerLocalOverwrite(request: LocalOverwriteRequest, replace: Boolean) {
        val confirmation = synchronized(operationLaunchLock) {
            localOverwriteConfirmation?.takeIf { it.request === request }
        }
        confirmation?.answer?.complete(replace)
    }

    private suspend fun requestLocalDelete(file: File): Boolean {
        val confirmation = LocalDeleteConfirmation(LocalDeleteRequest(file), CompletableDeferred())
        synchronized(operationLaunchLock) {
            localDeleteConfirmation = confirmation
            _localTerminalDelete.postValue(confirmation.request)
        }
        return try {
            confirmation.answer.await()
        } finally {
            synchronized(operationLaunchLock) {
                if (localDeleteConfirmation === confirmation) {
                    localDeleteConfirmation = null
                    _localTerminalDelete.postValue(null)
                }
            }
        }
    }

    internal fun answerLocalDelete(request: LocalDeleteRequest, delete: Boolean) {
        val confirmation = synchronized(operationLaunchLock) {
            localDeleteConfirmation?.takeIf { it.request === request }
        }
        confirmation?.answer?.complete(delete)
    }

    private suspend fun requestLocalScriptConfirmation(message: String): Boolean {
        val confirmation = LocalScriptConfirmation(LocalScriptConfirmationRequest(message), CompletableDeferred())
        synchronized(operationLaunchLock) {
            localScriptConfirmationState?.answer?.complete(false)
            localScriptConfirmationState = confirmation
            _localScriptConfirmation.postValue(confirmation.request)
        }
        return try {
            confirmation.answer.await()
        } finally {
            synchronized(operationLaunchLock) {
                if (localScriptConfirmationState === confirmation) {
                    localScriptConfirmationState = null
                    _localScriptConfirmation.postValue(null)
                }
            }
        }
    }

    internal fun answerLocalScriptConfirmation(request: LocalScriptConfirmationRequest, approved: Boolean) {
        val confirmation = synchronized(operationLaunchLock) {
            localScriptConfirmationState?.takeIf { it.request === request }
        }
        confirmation?.answer?.complete(approved)
    }

    internal fun launchLocalTerminalCommand(
        scope: CoroutineScope,
        block: suspend (ImportCancellation) -> Unit
    ): LocalTerminalAdmission {
        val operation = synchronized(operationLaunchLock) {
            if (localTerminalOperation != null) return LocalTerminalAdmission.LOCAL_BUSY
            if (viewModelCleared.get() || connectionJob?.isCompleted == false || hasUnfinishedUsbOperation()) {
                return LocalTerminalAdmission.USB_BUSY
            }
            if (workspaceImport.isBusy) return LocalTerminalAdmission.WORKSPACE_BUSY

            val cancellation = ImportCancellation()
            val job = scope.launch(start = CoroutineStart.LAZY) { block(cancellation) }
            val local = LocalTerminalOperation(job, cancellation)
            localTerminalOperation = local
            _localTerminalRunning.postValue(true)
            job.invokeOnCompletion {
                synchronized(operationLaunchLock) {
                    // Keep the reservation through cancellation and temporary-file cleanup.
                    if (localTerminalOperation === local) {
                        localTerminalOperation = null
                        _localTerminalCopyProgress.postValue(null)
                        _localTerminalRunning.postValue(false)
                    }
                }
            }
            local
        }
        operation.job.start()
        return LocalTerminalAdmission.STARTED
    }

    internal fun cancelLocalTerminalCommand() {
        val serviceOwned = localScriptServiceOwned
        val operation = synchronized(operationLaunchLock) {
            localScriptConfirmationState?.answer?.complete(false)
            localTerminalOperation
        }
        if (serviceOwned) {
            cancelActiveOperation()
            return
        }
        if (operation == null) return
        if (localScriptFastbootActive) fastbootProtocol?.cancel()
        if (operation.cancellation.cancel()) operation.job.cancel()
    }

    fun runFastbootCommand(cmd: String, heavy: Boolean = true, expectedSerial: String? = null) {
        startOperation(
            text(R.string.notif_fastboot_command),
            text(R.string.notif_executing, cmd),
            heavy = heavy,
            expectedFastbootSerial = expectedSerial,
            requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT
        ) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val ok = if (heavy) proto.sendCommand(cmd) else proto.runTerminalCommand(cmd)
            if (!ok) failOperation(text(R.string.rev11_fastboot_command_failed, cmd))
        }
    }

    internal fun runFastbootTerminalPlan(steps: List<FastbootExecutionStep>, expectedSerial: String? = null, sparseLimit: Long = 0) {
        val heavy = steps.any { it !is FastbootExecutionStep.Raw && it !is FastbootExecutionStep.Inspect && it !is FastbootExecutionStep.PartitionCommand }
        startOperation(text(R.string.notif_fastboot_command), text(R.string.rev21_preparing_plan),
            heavy = heavy, expectedFastbootSerial = expectedSerial,
            requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
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
                        val result = flashImage(proto, partition, FirmwareSource.WorkspaceFile(file), sparseLimit) { context.ensureActive() }
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
                val formatter = FastbootFilesystemFormatter(
                    BundledFilesystemTools.fromDirectory(File(getApplication<Application>().applicationInfo.nativeLibraryDir)),
                    nextData = AndroidImageExtents::nextData, log = ::log)
                FastbootPlanExecutor(port, formatter, directory, { context.ensureActive() }, ::log, sparseLimit) { detail ->
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
            text(R.string.notif_executing, "set_active:$slot"),
            requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT
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
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, commandAfterDownload), expectedFastbootSerial = expectedSerial, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.downloadAndRun(file, commandAfterDownload)) {
                failOperation(text(R.string.rev11_fastboot_download_failed, commandAfterDownload))
            }
        }
    }


    fun runFastbootLogicalPartitionCommand(command: String) {
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, command), requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.runLogicalPartitionCommand(command)) failOperation(text(R.string.rev11_fastboot_logical_failed, command))
        }
    }

    fun inspectFastbootLogicalPartition(partition: String, expectedSerial: String? = null) {
        startOperation(text(R.string.notif_fastboot_diagnostics), text(R.string.notif_updating_device), heavy = false, expectedFastbootSerial = expectedSerial, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (proto.inspectLogicalPartition(partition) == null) {
                failOperation(text(R.string.rev11_fastboot_logical_info_failed, partition))
            }
        }
    }

    fun runFastbootFetch(partition: String, outputFile: File, slot: String? = null, expectedSerial: String? = null) {
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, "fetch $partition"), expectedFastbootSerial = expectedSerial, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
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
        startOperation(text(R.string.notif_fastboot_command), text(R.string.notif_executing, label), heavy = false, expectedFastbootSerial = expectedSerial, requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
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
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, service), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.runService(service)) failOperation("ADB service failed: $service")
            if (AdbServiceCompletionPolicy.expectsOneWayDisconnect(service)) {
                confirmSuccess("Reboot command sent. The device will temporarily disconnect and appear in the new mode.")
            }
        }
    }

    fun runAdbShell(command: String, redirect: AdbProtocol.ShellRedirect? = null) {
        val interactive = command.isBlank()
        val label = when {
            interactive -> "interactive shell"
            redirect != null -> "shell $command > ${redirect.target.name}"
            else -> "shell $command"
        }
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
            heavy = !interactive,
            requiredTransport = UsbOperationAdmission.RequiredTransport.ADB
        ) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.runShellCommand(command, redirect = redirect)) failOperation("ADB shell failed: $label")
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

    fun runAdbPush(localFile: File, remotePath: String) = runAdbPush(listOf(localFile), remotePath)

    internal fun runAdbPush(localFiles: List<File>, remotePath: String) {
        val sourceNames = localFiles.joinToString(" ") { it.name }
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "push $sourceNames $remotePath"), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            val context = kotlinx.coroutines.currentCoroutineContext()
            if (!proto.pushPaths(localFiles, remotePath) { context.ensureActive() }) failOperation("ADB push failed")
        }
    }

    internal data class AdbPushLocation(val protocol: AdbProtocol, val directory: String)

    // The document picker can return to a recreated Activity in the same device session.
    internal var pendingAdbPushLocation: AdbPushLocation? = null

    internal fun runAdbPushDocuments(documents: List<Uri>, location: AdbPushLocation): Boolean {
        return startOperation(
            text(R.string.notif_adb_command),
            text(R.string.notif_executing, "push ${location.directory}"),
            requiredTransport = UsbOperationAdmission.RequiredTransport.ADB
        ) {
            val protocol = location.protocol
            if (adbProtocol !== protocol || !protocol.isConnected) {
                failOperation(text(R.string.adb_device_files_connection_changed))
            }
            val context = kotlinx.coroutines.currentCoroutineContext()
            val resolver = getApplication<Application>().contentResolver
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            val sources = documents.mapIndexed { index, uri ->
                context.ensureActive()
                var name: String? = null
                var size: Long? = null
                runCatching {
                    resolver.query(uri, columns, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0) name = cursor.getString(nameIndex)
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                            }
                        }
                    }
                }
                context.ensureActive()
                val filename = name?.takeIf(AdbDeviceDirectory::isChildName)
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(AdbDeviceDirectory::isChildName)
                    ?: "document-${index + 1}"
                AdbProtocol.PushSource(filename, size, (System.currentTimeMillis() / 1000L).toInt()) {
                    context.ensureActive()
                    val descriptor = resolver.openAssetFileDescriptor(uri, "r")
                        ?: throw IOException("Could not open selected document: $filename")
                    val input = try { descriptor.createInputStream() } catch (error: Exception) {
                        descriptor.close()
                        throw error
                    }
                    CancellableProviderInputStream(input, descriptor.fileDescriptor) {
                        context.ensureActive()
                        if (!protocol.isConnected) throw IOException("ADB transport disconnected")
                    }
                }
            }
            if (!protocol.pushDocuments(sources, location.directory, context::ensureActive)) {
                failOperation("ADB push failed")
            }
        } != null
    }

    fun runAdbPull(remotePath: String, localFile: File) {
        runAdbPull(listOf(remotePath), localFile)
    }

    internal fun runAdbPull(remotePath: String, localFile: File, expectedProtocol: AdbProtocol) =
        runAdbPull(listOf(remotePath), localFile, expectedProtocol)

    internal fun runAdbPull(remotePaths: List<String>, localFile: File, expectedProtocol: AdbProtocol? = null): Boolean {
        val sourceNames = remotePaths.joinToString(" ")
        return startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "pull $sourceNames"), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (expectedProtocol != null && proto !== expectedProtocol) failOperation("ADB connection changed before starting the transfer")
            val context = kotlinx.coroutines.currentCoroutineContext()
            if (!proto.pullFiles(remotePaths, localFile) { context.ensureActive() }) failOperation("ADB pull failed")
        } != null
    }

    internal suspend fun listAdbDeviceFiles(
        protocol: AdbProtocol,
        path: String
    ): List<AdbDeviceDirectory.Entry>? {
        var listing: List<AdbDeviceDirectory.Entry>? = null
        val task = startOperation(
            text(R.string.adb_device_files),
            path,
            heavy = false,
            requiredTransport = UsbOperationAdmission.RequiredTransport.ADB
        ) {
            if (adbProtocol !== protocol || !protocol.isConnected) failOperation(text(R.string.error_no_adb))
            listing = protocol.listDeviceFiles(path)
        } ?: return null
        task.finished.await()
        return listing
    }

    fun runAdbInstall(packageFile: File, options: List<String>) {
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "install ${packageFile.name}"), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            val context = kotlinx.coroutines.currentCoroutineContext()
            if (!proto.installPackage(packageFile, options) { context.ensureActive() }) failOperation("APK installation failed")
        }
    }

    fun runAdbInstallMultiple(apkFiles: List<File>, options: List<String>) {
        val names = apkFiles.joinToString(" ") { it.name }
        startOperation(text(R.string.notif_adb_command), text(R.string.notif_executing, "install-multiple $names"), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (!proto.installMultipleApks(apkFiles, options)) failOperation("install-multiple failed")
        }
    }

    fun runFlash(partition: String, file: File, slot: String? = null, expectedSerial: String? = null) =
        runFlash(partition, FirmwareSource.WorkspaceFile(file), slot, expectedSerial)

    internal fun runFlash(partition: String, source: FirmwareSource, slot: String? = null,
        expectedSerial: String? = null, expectedSizeBytes: Long? = null) {
        val label = partition + slot?.let { " --slot=$it" }.orEmpty()
        startOperation(
            text(R.string.notif_flash_img),
            text(R.string.notif_flashing_partition, source.displayName, label),
            expectedFastbootSerial = expectedSerial,
            requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT
        ) {
            val proto = fastbootProtocol ?: failOperation(text(R.string.error_no_fastboot))
            if (!proto.isConnected) failOperation(text(R.string.error_no_fastboot))
            val targets = if (slot == null && FlashOperationDraftPolicy.isExplicitSlotTarget(partition)) {
                listOf(partition)
            } else {
                proto.resolveSlotPartitionTargets(partition, slot)
                    ?: failOperation(text(R.string.rev13_fastboot_slot_error, partition))
            }
            val context = kotlinx.coroutines.currentCoroutineContext()
            targets.forEach { target ->
                val result = flashImage(proto, target, source, expectedSizeBytes = expectedSizeBytes) { context.ensureActive() }
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

    /** Every entry point uses this host preparation; each packet retains the original DATA/flash transport. */
    private fun flashImage(proto: FastbootProtocol, partition: String, source: FirmwareSource,
        sparseLimit: Long = 0, expectedSizeBytes: Long? = null, checkCancelled: () -> Unit): FastbootProtocol.FlashResult {
        val app = getApplication<Application>()
        source.open(app.contentResolver).use { opened ->
            FirmwareSelection.from(source).copy(expectedSizeBytes = expectedSizeBytes).requireSize(opened.sizeBytes)
            postOperationProgress(OperationProgress(text(R.string.notif_flash_img), -1, text(R.string.rev23_preparing_image, source.displayName)))
            val image = FastbootFlashImage.prepare(opened, proto.hostDownloadLimitBytes(), sparseLimit, checkCancelled)
            checkCancelled()
            if (!image.needsResparse) return when (source) {
                is FirmwareSource.WorkspaceFile -> proto.flashPartitionDetailed(partition, source.file)
                is FirmwareSource.SafDocument -> proto.flashPartitionDetailed(partition, source, app.contentResolver)
            }
            // Exclusive service admission makes it safe to remove only our interrupted part directories.
            app.cacheDir.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("fastboot-sparse-[0-9a-f-]{36}")) }
                ?.forEach { checkCancelled(); check(it.deleteRecursively()) { "Could not remove interrupted sparse packets" } }
            val directory = File(app.cacheDir, "fastboot-sparse-${UUID.randomUUID()}")
            check(directory.mkdirs()) { "Could not create sparse packet workspace" }
            var sent = 0L
            var completed = 0L
            var result = FastbootProtocol.FlashResult.ok()
            try {
                require(hasImmediateSparseSpace(directory, image.largestPartBytes)) {
                    "Not enough temporary storage for a sparse packet: need ${image.largestPartBytes} bytes"
                }
                log("Resparse ${source.displayName}: ${image.partCount} packets, limit=${image.limitBytes}, expanded=${image.expandedBytes}, download=${image.downloadBytes}")
                image.forEachPart(directory) { part, index, total ->
                    checkCancelled()
                    val label = text(R.string.rev23_image_part, index, total)
                    sparseProgress = SparseProgress(label, sent, part.length(), image.downloadBytes)
                    log("$label → flash:$partition (${part.length()} bytes)")
                    val current = proto.flashPartitionDetailed(partition, part)
                    sent = Math.addExact(sent, current.dataBytesTransferred)
                    if (current.success) completed++
                    result = current.copy(dataBytesTransferred = sent,
                        message = if (current.success) current.message else "$label: ${current.message}. Completed packets: $completed/$total; no automatic retry.")
                    current.success
                }
                return result
            } finally {
                sparseProgress = null
                if (!directory.deleteRecursively()) log("Could not remove sparse packet workspace: ${directory.name}")
                if (completed < image.partCount) log("Resparse stopped after $completed/${image.partCount} completed packets. Previous writes were not rolled back.")
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

    internal fun currentQuickFlashSelection(): FirmwareSelection? =
        quickSourceSnapshot.takeIf { it.ready }?.selection

    internal fun selectQuickFlashSource(source: FirmwareSource) = checkQuickFlashSource(FirmwareSelection.from(source))

    internal fun refreshQuickFlashSource() {
        if (quickSourceSnapshot.checking || operationJob != null) return
        quickSourceSnapshot.selection?.let(::checkQuickFlashSource)
    }

    private fun checkQuickFlashSource(selection: FirmwareSelection) {
        val generation = synchronized(quickSourceLock) { ++quickSourceGeneration }
        publishQuickSource(generation, QuickFlashSourceState(selection, checking = true))
        transportScope.launch {
            // Persist the user's new choice even when its provider is temporarily unavailable.
            publishQuickSource(generation, QuickFlashSourceState(selection, checking = true), persist = true)
            try {
                val checked = selection.verify(getApplication<Application>().contentResolver) { coroutineContext.ensureActive() }
                publishQuickSource(generation, QuickFlashSourceState(checked, ready = true), persist = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                publishQuickSource(generation, QuickFlashSourceState(selection,
                    error = text(R.string.rev24_source_unavailable, selection.displayName, error.message ?: error.javaClass.simpleName)))
            }
        }
    }

    private fun publishQuickSource(generation: Long, state: QuickFlashSourceState, persist: Boolean = false) {
        synchronized(quickSourceLock) {
            if (generation != quickSourceGeneration || viewModelCleared.get()) return
            if (persist) runCatching { recoveryStore.writeQuickSource(requireNotNull(state.selection)) }
                .onFailure { log(text(R.string.rev24_source_save_failed, it.message ?: it.javaClass.simpleName)) }
            quickSourceSnapshot = state
            mainHandler.post {
                synchronized(quickSourceLock) {
                    if (generation == quickSourceGeneration && !viewModelCleared.get()) _quickFlashSource.value = state
                }
            }
        }
    }

    /** Read/probe a document off the UI thread; stale picker results cannot replace a later choice. */
    internal fun addFlashQueueSource(partition: String, source: FirmwareSource) =
        addFlashQueueSources(listOf(partition to source))

    internal fun addFlashQueueSources(sources: List<Pair<String, FirmwareSource>>) {
        if (sources.isEmpty()) return
        val byTarget = sources.associate { (partition, source) -> partition.trim() to source }
        val generation = synchronized(flashDraftLock) {
            (++queueSourceGeneration).also { generation ->
                byTarget.keys.forEach { target -> pendingQueueSources[target] = generation }
            }
        }
        publishQueueSourceState()
        transportScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val results = byTarget.mapValues { (target, source) ->
                coroutineContext.ensureActive()
                runCatching {
                    FirmwareSelection.from(source).verify(resolver) { coroutineContext.ensureActive() }
                        .queueItem(target)
                }
            }
            coroutineContext.ensureActive()
            mainHandler.post {
                if (viewModelCleared.get()) return@post
                val current = synchronized(flashDraftLock) {
                    results.filterKeys { target ->
                        if (pendingQueueSources[target] != generation) false
                        else {
                            pendingQueueSources.remove(target)
                            true
                        }
                    }
                }
                if (current.isEmpty()) return@post
                val errors = current.mapNotNull { (target, result) ->
                    result.exceptionOrNull()?.let { error ->
                        text(R.string.rev24_queue_add_failed, byTarget.getValue(target).displayName,
                            error.message ?: error.javaClass.simpleName)
                    }
                }
                addFlashQueueItems(current.mapNotNull { (_, result) -> result.getOrNull() })
                publishQueueSourceState(errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                errors.forEach(::log)
            }
        }
    }

    private fun publishQueueSourceState(error: String? = null) {
        _queueSourceState.value = QueueSourceState(synchronized(flashDraftLock) { pendingQueueSources.keys.toSet() }, error)
    }

    private fun invalidatePendingQueueSource(partition: String? = null) {
        synchronized(flashDraftLock) {
            if (partition == null) pendingQueueSources.clear() else pendingQueueSources.remove(partition)
        }
        publishQueueSourceState()
    }

    fun addFlashQueueFile(partition: String, file: File) {
        invalidatePendingQueueSource(partition.trim())
        val item = runCatching { FlashOperationDraftPolicy.createItem(partition, file) }
            .getOrElse { error ->
                log("❌ Could not add file to the queue: ${error.message ?: error.javaClass.simpleName}")
                return
            }
        addFlashQueueItems(listOf(item))
    }

    private fun addFlashQueueItems(items: List<FlashQueueDraftItem>) {
        if (items.isEmpty()) return
        val next = runCatching { items.fold(currentFlashOperationDraft(), FlashOperationDraftPolicy::upsert) }
            .getOrElse { error ->
                log("❌ Could not update queue: ${error.message ?: error.javaClass.simpleName}")
                return
            }
        publishFlashOperationDraft(next, persist = true)
        items.forEach { item -> log(text(R.string.flash_queue_updated_log, item.partition, item.displayName)) }
    }

    fun moveFlashQueueDraftItem(partition: String, direction: Int) {
        val next = FlashOperationDraftPolicy.move(currentFlashOperationDraft(), partition, direction)
        publishFlashOperationDraft(next, persist = true)
    }

    fun removeFlashQueueDraftItem(partition: String) {
        invalidatePendingQueueSource(partition)
        val next = FlashOperationDraftPolicy.remove(currentFlashOperationDraft(), partition)
        publishFlashOperationDraft(next, persist = true)
    }

    fun clearFlashQueueDraft() {
        invalidatePendingQueueSource()
        publishFlashOperationDraft(
            FlashOperationDraftPolicy.clear(currentFlashOperationDraft()),
            persist = true
        )
        log(text(R.string.flash_queue_cleared_log))
    }

    internal fun setFlashQueueSlot(slot: String?) {
        val draft = currentFlashOperationDraft()
        val selected = FlashOperationDraftPolicy.selectSlot(draft, slot)
        if (selected != draft) publishFlashOperationDraft(selected, persist = true)
    }

    internal suspend fun previewFlashQueueDraft(draft: FlashOperationDraft): List<MassFlashPlan.Step> =
        withContext(Dispatchers.IO) {
            val generation = connectionGeneration.get()
            val proto = fastbootProtocol
            val steps = MassFlashPlan.resolve(draft) { partition, slot ->
                ensureActive()
                val available = synchronized(operationLaunchLock) {
                    !viewModelCleared.get() && generation == connectionGeneration.get() &&
                        proto === fastbootProtocol && proto?.isConnected == true &&
                        connectionJob?.isCompleted != false && !hasUnfinishedUsbOperation()
                }
                // The existing resolver holds the Fastboot transaction lock for its getvar sequence.
                if (available) proto?.resolveSlotPartitionTargets(partition, slot) else null
            }
            if (generation == connectionGeneration.get() && proto === fastbootProtocol &&
                (proto == null || proto.isConnected)
            ) steps else MassFlashPlan.resolve(draft) { _, _ -> null }
        }

    fun executeFlashQueueDraft() {
        if (synchronized(flashDraftLock) { pendingQueueSources.isNotEmpty() }) {
            log(text(R.string.rev24_queue_checking, synchronized(flashDraftLock) { pendingQueueSources.keys.joinToString() }))
            return
        }
        val draft = currentFlashOperationDraft()
        if (draft.items.isEmpty()) {
            log(text(R.string.flash_queue_empty_log))
            return
        }
        runFlashQueue(draft)
    }

    private fun publishFlashOperationDraft(draft: FlashOperationDraft, persist: Boolean) {
        synchronized(flashDraftLock) { flashDraftSnapshot = draft }
        if (persist) {
            runCatching { recoveryStore.writeDraft(draft) }
                .onFailure { log(text(R.string.rev20_draft_save_failed, it.message ?: it.javaClass.simpleName)) }
        }
        _flashOperationDraft.value = draft
    }

    private fun runFlashQueue(draft: FlashOperationDraft) {
        val ordered = draft.items
        val requested = MassFlashPlan.resolve(draft.copy(slot = null)) { _, _ -> null }
        fun publishQueueSteps(targets: List<MassFlashPlan.Step>) {
            setOperationSteps(targets.mapIndexed { index, item ->
                OperationStep(
                    index = index + 1,
                    total = targets.size,
                    title = "flash ${item.target ?: item.source.partition} ← ${item.source.displayName}",
                    subtitle = formatBytesShort(item.source.expectedSizeBytes),
                    status = OperationStepStatus.PENDING
                )
            })
        }

        val slotLabel = draft.slot?.let { " --slot=$it" }.orEmpty()
        startOperation(text(R.string.notif_flash_img), "Flash queue: ${ordered.size} item(s)$slotLabel. Do not disconnect the cable.", requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT) {
            fun failStep(index: Int, reason: String): Nothing {
                markOperationStep(index + 1, OperationStepStatus.FAILED, reason)
                val summary = completeFlashQueueSteps(R.string.mass_flash_step_not_run)
                failOperation("$reason\n$summary")
            }

            publishQueueSteps(requested)
            try {
                val proto = fastbootProtocol
                if (proto?.isConnected != true) failStep(0, text(R.string.error_no_fastboot))
                val context = kotlinx.coroutines.currentCoroutineContext()
                // Probe every source before the first download; persisted URI metadata is not a grant.
                ordered.forEachIndexed { index, item ->
                    try {
                        FirmwareSelection.from(item).verify(getApplication<Application>().contentResolver) { context.ensureActive() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val reason = text(R.string.rev24_source_unavailable, item.displayName, error.message ?: error.javaClass.simpleName)
                        failStep(index, reason)
                    }
                }
                val resolved = MassFlashPlan.resolve(draft) { partition, slot ->
                    context.ensureActive()
                    proto.resolveSlotPartitionTargets(partition, slot)
                }
                context.ensureActive()
                if (resolved != requested) publishQueueSteps(resolved)
                val unresolved = resolved.indexOfFirst { it.target == null }
                if (unresolved >= 0) {
                    failStep(unresolved, text(R.string.rev13_fastboot_slot_error, resolved[unresolved].source.partition))
                }
                val repeated = MassFlashPlan.repeatedTargets(resolved)
                if (repeated.isNotEmpty()) log(text(R.string.mass_flash_repeated_targets, repeated.joinToString()))

                resolved.forEachIndexed { index, item ->
                    context.ensureActive()
                    val target = requireNotNull(item.target)
                    val stepNumber = index + 1
                    markOperationStep(stepNumber, OperationStepStatus.RUNNING, "fastboot flash $target")
                    log("=== FLASH QUEUE $stepNumber/${resolved.size}: $target ← ${item.source.displayName} ===")
                    val result = try {
                        flashImage(proto, target, FirmwareSelection.from(item.source).source(), expectedSizeBytes = item.source.expectedSizeBytes) { context.ensureActive() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failStep(index, text(R.string.mass_flash_step_error, target, error.message ?: error.javaClass.simpleName))
                    }
                    val diagnostics = proto.currentDiagnostics()
                    if (diagnostics != null) _fastbootDiagnostics.postValue(diagnostics)
                    if (!result.success) {
                        context.ensureActive()
                        failStep(index, formatFlashFailure(target, result))
                    }
                    markOperationStep(stepNumber, OperationStepStatus.OK, diagnosticsBrief(diagnostics))
                }
                val summary = completeFlashQueueSteps(R.string.mass_flash_step_not_run)
                log("✅ $summary")
                confirmSuccess(summary)
            } catch (cancelled: CancellationException) {
                log(completeFlashQueueSteps(R.string.operation_cancelled))
                throw cancelled
            }
        }
    }

    private fun completeFlashQueueSteps(skippedDetail: Int): String {
        val finalSteps = synchronized(operationStepLock) {
            operationStepSnapshot.map { step ->
                when (step.status) {
                    OperationStepStatus.PENDING, OperationStepStatus.RUNNING ->
                        step.copy(status = OperationStepStatus.SKIPPED, subtitle = text(skippedDetail))
                    else -> step
                }
            }
        }
        setOperationSteps(finalSteps)
        return text(
            R.string.mass_flash_result,
            finalSteps.count { it.status == OperationStepStatus.OK },
            finalSteps.count { it.status == OperationStepStatus.FAILED },
            finalSteps.count { it.status == OperationStepStatus.SKIPPED }
        )
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

    private fun persistPendingSideloadVerification(source: FirmwareSource, proto: AdbProtocol): PendingSideloadVerification {
        val serial = currentUsbSerial()
        val now = System.currentTimeMillis()
        val sourceRecovery = SideloadRecoveryTransitionPolicy.fromAdbBanner(
            proto.currentDiagnostics().remoteBanner,
            connectedUsbTarget?.device?.vendorId ?: -1,
            connectedUsbTarget?.device?.productId ?: -1
        )
        val prepared = preparedRecoveryLog
        preparedRecoveryLog = null // A boundary belongs to one accepted attempt only.
        val boundary = prepared?.takeIf {
            it.serial == serial && now - it.createdAtMs in 0L..SIDELOAD_VERIFY_TIMEOUT_MS
        }?.boundary
        val attempt = PendingSideloadVerification(
            UUID.randomUUID().toString(), source.displayName, source.sizeHint ?: -1L,
            serial, now, boundary, false, sourceRecovery, false
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
            .putString(SIDELOAD_VERIFY_RECOVERY_NAME, sourceRecovery?.productName)
            .putString(SIDELOAD_VERIFY_RECOVERY_DEVICE, sourceRecovery?.deviceCodename)
            .putInt(SIDELOAD_VERIFY_RECOVERY_VID, sourceRecovery?.vendorId ?: -1)
            .putInt(SIDELOAD_VERIFY_RECOVERY_PID, sourceRecovery?.productId ?: -1)
            .putBoolean(SIDELOAD_VERIFY_RECOVERY_CHANGED, false)
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
            transferFinished = prefs.getBoolean(SIDELOAD_VERIFY_TRANSFER_FINISHED, false),
            sourceRecovery = prefs.getString(SIDELOAD_VERIFY_RECOVERY_NAME, null)?.let { name ->
                SideloadRecoveryTransitionPolicy.Identity(
                    name,
                    prefs.getString(SIDELOAD_VERIFY_RECOVERY_DEVICE, null).orEmpty(),
                    prefs.getInt(SIDELOAD_VERIFY_RECOVERY_VID, -1),
                    prefs.getInt(SIDELOAD_VERIFY_RECOVERY_PID, -1)
                )
            },
            recoveryTransitionObserved = prefs.getBoolean(SIDELOAD_VERIFY_RECOVERY_CHANGED, false)
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

    /**
     * A Recovery ZIP may replace the running Recovery and return directly to
     * SIDELOAD mode. Strict same-boot /tmp/recovery.log validation must remain
     * authoritative for installation SUCCESS/FAILED. Observe a verified
     * environment switch separately, without replaying archived legacy logs.
     */
    private fun observePendingSideloadRecoveryTransition(proto: AdbProtocol) {
        if (proto.peerMode != AdbProtocol.PeerMode.RECOVERY &&
            proto.peerMode != AdbProtocol.PeerMode.SIDELOAD) return
        val pending = readPendingSideloadVerification() ?: return
        if (!pending.transferFinished || pending.recoveryTransitionObserved) return
        val ageMs = System.currentTimeMillis() - pending.createdAtMs
        if (pending.createdAtMs <= 0L || ageMs < 0L || ageMs > SIDELOAD_VERIFY_TIMEOUT_MS) return
        val observed = SideloadRecoveryTransitionPolicy.fromAdbBanner(
            proto.currentDiagnostics().remoteBanner,
            connectedUsbTarget?.device?.vendorId ?: -1,
            connectedUsbTarget?.device?.productId ?: -1
        )
        val transition = SideloadRecoveryTransitionPolicy.observe(
            pending.sourceRecovery, observed,
            sameUsbSerial = pending.serial != null && pending.serial == currentUsbSerial(),
            transferFinished = pending.transferFinished
        ) ?: return
        val prefs = sideloadVerificationPrefs()
        if (prefs.getString(SIDELOAD_VERIFY_ATTEMPT_ID, null) != pending.attemptId ||
            prefs.getBoolean(SIDELOAD_VERIFY_RECOVERY_CHANGED, false)) return
        @Suppress("UseKtx")
        if (!prefs.edit().putBoolean(SIDELOAD_VERIFY_RECOVERY_CHANGED, true).commit()) {
            logFileOnly("Could not persist observed Recovery transition for Sideload ${pending.attemptId}")
            return
        }
        val detail = transition.detail(pending.packageName)
        log("ℹ️ $detail")
        publishSideloadRecoveryVerdict(
            pending.attemptId,
            detail,
            OperationOutcomeKind.VERIFY_PENDING
        )
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
                val current = operationProgressSnapshot
                if (activeServiceOperation != null || _operationActive.value == true ||
                    current?.title != pending.operationLabel || current?.outcome != OperationOutcomeKind.VERIFY_PENDING) {
                    logFileOnly("Unlock verification kept in diagnostics; another operation owns the GUI result.")
                    return
                }
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
                val current = operationProgressSnapshot
                if (activeServiceOperation != null || _operationActive.value == true ||
                    current?.title != pending.operationLabel || current?.outcome != OperationOutcomeKind.VERIFY_PENDING) {
                    logFileOnly("Unlock verification kept in diagnostics; another operation owns the GUI result.")
                    return
                }
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
        startOperation(text(R.string.notif_adb_sideload), text(R.string.notif_sideload_sending, source.displayName), requiredTransport = UsbOperationAdmission.RequiredTransport.ADB) {
            val proto = adbProtocol ?: failOperation(text(R.string.error_no_adb))
            if (!proto.isConnected) failOperation(text(R.string.error_no_adb))
            if (proto.peerMode != AdbProtocol.PeerMode.SIDELOAD) {
                failOperation(text(R.string.rev12_sideload_mode_inactive, proto.peerMode.name))
            }
            // Admission comes first: a rejected second click cannot clear an active marker.
            val attempt = persistPendingSideloadVerification(source, proto)
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

    private fun hasUnfinishedUsbOperation(): Boolean = operationJob != null ||
        NativeUsbfsBackend.hasActiveTransfer || NativeUsbfsBackend.backendState().nativeTransferActive

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
        requiredTransport: UsbOperationAdmission.RequiredTransport = UsbOperationAdmission.RequiredTransport.ANY_USB,
        block: suspend OperationContext.() -> Unit
    ): ServiceOwnedOperation? {
        synchronized(operationLaunchLock) {
            if (viewModelCleared.get()) {
                log("⚠️ New operation rejected: USB session is finishing safe shutdown.")
                return null
            }
            if (transportRestartRequired.get()) {
                log("⛔ New operation is blocked: USB transport requires a full NekoFlash restart after unconfirmed cleanup.")
                return null
            }
            if (connectionJob?.isCompleted == false) {
                log("⚠️ USB connect or disconnect is still in progress. Wait for a stable device status.")
                return null
            }
            if (localTerminalOperation != null) {
                log(DiagnosticLogPolicy.Level.WARNING, text(R.string.local_terminal_usb_blocked))
                return null
            }
            // A completed Job can still be delivering its completion callbacks.
            // The reference is cleared only after state/resources have been finalized.
            if (hasUnfinishedUsbOperation()) {
                log("⚠️ Another USB operation is still active. Wait for it to finish first or perform a safe cancel.")
                return null
            }
            if (!UsbOperationAdmission.allowed(
                    requiredTransport,
                    fastbootProtocol?.isConnected == true,
                    adbProtocol?.isConnected == true
                )) {
                // Android 14+ refuses connectedDevice FGS promotion if no
                // qualifying live transport/USB grant exists. Reject locally
                // before persisting PREPARING or starting a service. No command
                // is sent, and no stale Operation Center result is created.
                val reason = when (requiredTransport) {
                    UsbOperationAdmission.RequiredTransport.FASTBOOT -> text(R.string.error_no_fastboot)
                    UsbOperationAdmission.RequiredTransport.ADB -> text(R.string.error_no_adb)
                    UsbOperationAdmission.RequiredTransport.ANY_USB ->
                        "No connected ADB/Fastboot device. Connect the target over OTG."
                }
                log(DiagnosticLogPolicy.Level.ERROR, "ERROR: $reason Command was not sent.")
                return null
            }
            val gen = operationGeneration.incrementAndGet()
            val id = UUID.randomUUID().toString()
            sideloadGuiAttemptId = null
            val pending = PersistedOperation(id, title, text, System.currentTimeMillis(), -1, PersistedOperationPhase.PREPARING)
            val result = AtomicReference<OperationOutcome>(OperationOutcome.Success)
            lateinit var task: ServiceOwnedOperation
            task = ServiceOwnedOperation(
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
                val context = OperationContext { message -> confirmOperationSuccess(task, message) }
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
            terminalOperationId = id.takeIf { terminalSubmission.get() == true }
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
            return task
        }
    }

    /** Persist the received terminal result before transport shutdown can cancel the Job. */
    internal fun confirmOperationSuccess(task: ServiceOwnedOperation, message: String) {
        check(task.confirmSuccess(message)) { "Operation cannot accept a terminal success receipt" }
        synchronized(checkpointLock) {
            val current = persistedOperation?.takeIf { it.id == task.id } ?: return
            val confirmed = current.copy(detail = message, percent = 100, phase = PersistedOperationPhase.SUCCESS)
            persistedOperation = confirmed
            runCatching { recoveryStore.writeOperation(confirmed) }
                .onFailure { logFileOnly("Confirmed operation checkpoint failed: ${it.message}") }
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
                // A real terminal response outranks a later detach/cancel/service cleanup.
                task.confirmedSuccessMessage != null -> OperationOutcome.Success
                task.serviceLost -> OperationOutcome.Interrupted(text(R.string.rev20_service_lost))
                // A Recovery-triggered USB detach can cancel this job after the
                // near-complete stream closure was observed. Keep VERIFY_PENDING,
                // never turn it into SUCCESS without installer evidence.
                reported is OperationOutcome.VerifyPending -> reported
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
                OperationOutcome.Success -> task.confirmedSuccessMessage
                    ?: if (task.tracksProgress) operationProgressSnapshot?.detail.orEmpty() else text(R.string.layout_operation_center_completed)
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
            if (terminalOperationId == task.id) {
                val terminalResult = when (kind) {
                    OperationOutcomeKind.SUCCESS -> text(R.string.rev26_terminal_completed)
                    OperationOutcomeKind.CANCELLED -> text(R.string.operation_cancelled)
                    OperationOutcomeKind.VERIFY_PENDING -> message
                    else -> null // Failure is emitted by the journal below.
                }
                synchronized(logLock) { terminalResult?.let(::appendTerminalLocked) }
                terminalOperationId = null
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
            logFileOnly(text(R.string.wake_lock_acquired))
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
            if (logRelease) logFileOnly(text(R.string.wake_lock_released))
        } catch (e: Exception) {
            if (logRelease) log(text(R.string.wake_lock_release_error, e.message ?: e.javaClass.simpleName))
        }
    }

    fun cancelActiveOperation() {
        synchronized(operationLaunchLock) {
            localScriptConfirmationState?.answer?.complete(false)
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
        viewModelCleared.set(true)
        cancelLocalTerminalCommand()
        localTerminalScope.cancel()
        workspaceImport.close()
        mainHandler.removeCallbacksAndMessages(null)
        flushDiagnostics("VIEWMODEL_CLEARED", terminal = true)
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
        private const val SIDELOAD_VERIFY_RECOVERY_NAME = "source_recovery_name"
        private const val SIDELOAD_VERIFY_RECOVERY_DEVICE = "source_recovery_device"
        private const val SIDELOAD_VERIFY_RECOVERY_VID = "source_recovery_vid"
        private const val SIDELOAD_VERIFY_RECOVERY_PID = "source_recovery_pid"
        private const val SIDELOAD_VERIFY_RECOVERY_CHANGED = "recovery_transition_observed"
        private const val SIDELOAD_VERIFY_CREATED_AT = "created_at"
    }
}
