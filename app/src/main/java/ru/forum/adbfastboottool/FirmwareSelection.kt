package ru.forum.adbfastboottool

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.URI

/** Persisted identity/size, never a persisted descriptor, grant or permission to execute. */
internal data class FirmwareSelection(
    val sourceUri: String,
    val displayName: String,
    val expectedSizeBytes: Long? = null
) {
    init {
        require(sourceUri.isNotBlank() && displayName.isNotBlank())
        require(expectedSizeBytes == null || expectedSizeBytes > 0)
    }

    fun source(): FirmwareSource {
        val uri = URI(sourceUri)
        return when (uri.scheme) {
            "file" -> {
                require(uri.authority == null && uri.query == null && uri.fragment == null)
                FirmwareSource.WorkspaceFile(File(uri).canonicalFile)
            }
            "content" -> {
                require(!uri.authority.isNullOrBlank())
                FirmwareSource.SafDocument(Uri.parse(sourceUri), displayName, expectedSizeBytes)
            }
            else -> throw IOException("Unsupported image source")
        }
    }

    fun verify(resolver: ContentResolver? = null, checkCancelled: () -> Unit = {}): FirmwareSelection {
        checkCancelled()
        source().open(resolver).use { opened ->
            requireSize(opened.sizeBytes)
            val probe = ByteArray(1)
            opened.readFullyAt(opened.sizeBytes - 1, probe)
            checkCancelled()
            opened.readFullyAt(0, probe)
            checkCancelled()
            return copy(expectedSizeBytes = opened.sizeBytes)
        }
    }

    fun requireSize(actual: Long) {
        if (actual <= 0 || expectedSizeBytes != null && actual != expectedSizeBytes) {
            throw IOException("Source size changed or empty: expected=$expectedSizeBytes, actual=$actual")
        }
    }

    fun queueItem(partition: String): FlashQueueDraftItem = FlashQueueDraftItem(
        partition.trim(), sourceUri, displayName, requireNotNull(expectedSizeBytes), System.currentTimeMillis()
    ).also { require(FlashOperationDraftPolicy.isPersistable(it)) { "Invalid queue target/source" } }

    companion object {
        /** Picker size is only a hint. Capture the actual descriptor size on the first verification. */
        fun from(source: FirmwareSource) = FirmwareSelection(
            when (source) {
                is FirmwareSource.WorkspaceFile -> source.file.canonicalFile.toURI().toASCIIString()
                is FirmwareSource.SafDocument -> source.uri.toString()
            }, source.displayName
        )
        fun from(item: FlashQueueDraftItem) = FirmwareSelection(item.sourceUri, item.displayName, item.expectedSizeBytes)
    }
}

internal data class QuickFlashSourceState(
    val selection: FirmwareSelection? = null,
    val checking: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null
)

internal data class QueueSourceState(val pending: Set<String> = emptySet(), val error: String? = null)
