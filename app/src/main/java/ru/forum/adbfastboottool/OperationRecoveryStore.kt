package ru.forum.adbfastboottool

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties

internal enum class PersistedOperationPhase {
    PREPARING, RUNNING, SUCCESS, FAILED, CANCELLED, VERIFY_PENDING, INTERRUPTED
}

internal data class PersistedOperation(
    val id: String,
    val title: String,
    val detail: String,
    val startedAtMs: Long,
    val percent: Int,
    val phase: PersistedOperationPhase
) {
    fun afterProcessRestart(): PersistedOperation = when (phase) {
        PersistedOperationPhase.PREPARING, PersistedOperationPhase.RUNNING ->
            copy(phase = PersistedOperationPhase.INTERRUPTED)
        else -> this
    }
}

/** Metadata only: neither this file nor a saved draft is an executable/replay queue. */
internal class OperationRecoveryStore(private val directory: File) {
    private val operationFile get() = File(directory, "last-operation.properties")
    private val draftFile get() = File(directory, "flash-draft.txt")
    private val quickSourceFile get() = File(directory, "quick-source.properties")

    @Synchronized fun readOperation(): PersistedOperation? {
        if (!operationFile.exists()) return null
        val fields = Properties().apply { operationFile.inputStream().use(::load) }
        fun field(key: String) = fields.getProperty(key) ?: throw IOException("Missing operation field: $key")
        if (field("schema") != "1") throw IOException("Unknown operation journal schema")
        return try {
            PersistedOperation(field("id"), field("title"), field("detail"),
                field("startedAtMs").toLong(), field("percent").toInt(),
                PersistedOperationPhase.valueOf(field("phase"))).also {
                require(it.id.isNotBlank() && it.startedAtMs > 0L && it.percent in -1..100)
            }
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid operation journal", error)
        }
    }

    @Synchronized fun writeOperation(value: PersistedOperation) {
        val fields = Properties().apply {
            setProperty("schema", "1")
            setProperty("id", value.id)
            setProperty("title", value.title)
            setProperty("detail", value.detail)
            setProperty("startedAtMs", value.startedAtMs.toString())
            setProperty("percent", value.percent.toString())
            setProperty("phase", value.phase.name)
        }
        publish(operationFile) { fields.store(it, "NekoFlash operation state; never replay") }
    }

    @Synchronized fun readDraft(): FlashOperationDraft? =
        if (draftFile.exists()) FlashOperationDraftCodec.decode(draftFile.readLines(Charsets.UTF_8)) else null

    @Synchronized fun writeDraft(draft: FlashOperationDraft) {
        val bytes = FlashOperationDraftCodec.encode(draft).joinToString("\n").toByteArray(Charsets.UTF_8)
        publish(draftFile) { it.write(bytes) }
    }

    @Synchronized fun readQuickSource(): FirmwareSelection? {
        if (!quickSourceFile.exists()) return null
        val fields = Properties().apply { quickSourceFile.inputStream().use(::load) }
        if (fields.getProperty("schema") != "1") throw IOException("Unknown image selection schema")
        return try {
            FirmwareSelection(requireNotNull(fields.getProperty("uri")), requireNotNull(fields.getProperty("name")),
                fields.getProperty("size")?.toLong()).also { it.source() }
        } catch (error: Exception) { throw IOException("Invalid image selection", error) }
    }

    @Synchronized fun writeQuickSource(selection: FirmwareSelection) {
        selection.source() // Reject unsupported URI schemes before replacing a valid record.
        val fields = Properties().apply {
            setProperty("schema", "1")
            setProperty("uri", selection.sourceUri)
            setProperty("name", selection.displayName)
            selection.expectedSizeBytes?.let { setProperty("size", it.toString()) }
        }
        publish(quickSourceFile) { fields.store(it, "NekoFlash selected image; never replay") }
    }

    private fun publish(target: File, write: (FileOutputStream) -> Unit) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create operation state directory")
        val pending = FilePublication.temporarySibling(target)
        try {
            FileOutputStream(pending).use { output -> write(output); output.fd.sync() }
            FilePublication.replace(pending, target)
        } finally {
            if (pending.exists()) pending.delete()
        }
    }
}
