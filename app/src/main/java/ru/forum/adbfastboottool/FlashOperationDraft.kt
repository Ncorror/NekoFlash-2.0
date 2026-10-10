package ru.forum.adbfastboottool

import java.io.File
import java.net.URI
import java.util.Base64
import java.util.Locale

/** Lightweight persistable queue entry. No pre-hashing or mutation authorization state. */
data class FlashQueueDraftItem(
    val partition: String,
    val sourceUri: String,
    val displayName: String,
    val expectedSizeBytes: Long,
    val addedAtEpochMs: Long
)

data class FlashOperationDraft(
    val items: List<FlashQueueDraftItem> = emptyList(),
    val revision: Long = 0L
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

object FlashOperationDraftPolicy {
    private const val MAX_URI_LENGTH = 4096
    private const val MAX_DISPLAY_NAME_LENGTH = 256
    private val PARTITION_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun createItem(
        partition: String,
        file: File,
        addedAtEpochMs: Long = System.currentTimeMillis()
    ): FlashQueueDraftItem {
        val normalizedPartition = normalizePartition(partition)
            ?: throw IllegalArgumentException("Invalid partition name")
        val canonical = file.canonicalFile
        require(canonical.exists() && canonical.isFile && canonical.canRead()) {
            "Source file is not readable"
        }
        require(canonical.length() > 0L) { "Source file is empty" }
        return FlashQueueDraftItem(
            partition = normalizedPartition,
            sourceUri = canonical.toURI().toASCIIString(),
            displayName = canonical.name.take(MAX_DISPLAY_NAME_LENGTH),
            expectedSizeBytes = canonical.length(),
            addedAtEpochMs = addedAtEpochMs
        )
    }

    fun upsert(draft: FlashOperationDraft, item: FlashQueueDraftItem): FlashOperationDraft {
        require(isPersistable(item)) { "Draft item is not persistable" }
        val next = LinkedHashMap<String, FlashQueueDraftItem>()
        // LinkedHashMap.put replaces an existing target without changing its position.
        // Visible order is execution order; names are concrete targets, including _a/_b.
        draft.items.forEach { existing -> next[existing.partition] = existing }
        next[item.partition] = item
        return FlashOperationDraft(next.values.toList(), draft.revision + 1L)
    }

    /** Move a visible row by one place without sorting the queue. */
    fun move(draft: FlashOperationDraft, partition: String, delta: Int): FlashOperationDraft {
        require(delta == -1 || delta == 1) { "Only adjacent moves are supported" }
        val list = draft.items.toMutableList()
        val original = list.indexOfFirst { it.partition == partition }
        if (original < 0) return draft
        val destination = original + delta
        if (destination !in list.indices) return draft
        val item = list.removeAt(original)
        list.add(destination, item)
        return FlashOperationDraft(list.toList(), draft.revision + 1L)
    }

    fun remove(draft: FlashOperationDraft, partition: String): FlashOperationDraft {
        if (draft.items.none { it.partition == partition }) return draft
        return FlashOperationDraft(draft.items.filterNot { it.partition == partition }, draft.revision + 1L)
    }

    fun clear(draft: FlashOperationDraft): FlashOperationDraft =
        FlashOperationDraft(emptyList(), draft.revision + 1L)

    fun resolve(item: FlashQueueDraftItem): File? = runCatching {
        val uri = URI(item.sourceUri)
        if (!uri.scheme.equals("file", ignoreCase = true) || uri.authority != null) return null
        File(uri).canonicalFile.takeIf { it.exists() && it.isFile && it.canRead() && it.length() > 0L }
    }.getOrNull()

    fun isPersistable(item: FlashQueueDraftItem): Boolean =
        normalizePartition(item.partition) == item.partition &&
            item.sourceUri.isNotBlank() && item.sourceUri.length <= MAX_URI_LENGTH &&
            item.displayName.isNotBlank() && item.displayName.length <= MAX_DISPLAY_NAME_LENGTH &&
            item.expectedSizeBytes > 0L &&
            item.addedAtEpochMs >= 0L

    private fun normalizePartition(value: String): String? {
        val normalized = value.trim().lowercase(Locale.US)
        return normalized.takeIf { PARTITION_PATTERN.matches(it) }
    }
}

/** Bundle/SavedStateHandle-safe codec for the lightweight queue. */
object FlashOperationDraftCodec {
    private const val SCHEMA = "2"
    private const val SEPARATOR = '|'
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(draft: FlashOperationDraft): ArrayList<String> = ArrayList(
        draft.items
            .filter(FlashOperationDraftPolicy::isPersistable)
            .map { item ->
                listOf(
                    SCHEMA,
                    encodeField(item.partition),
                    encodeField(item.sourceUri),
                    encodeField(item.displayName),
                    item.expectedSizeBytes.toString(),
                    item.addedAtEpochMs.toString()
                ).joinToString(SEPARATOR.toString())
            }
    )

    fun decode(encoded: List<String>?): FlashOperationDraft {
        if (encoded.isNullOrEmpty()) return FlashOperationDraft()
        val items = LinkedHashMap<String, FlashQueueDraftItem>()
        encoded.forEach { row ->
            val parts = row.split(SEPARATOR)
            if (parts.size != 6 || parts[0] != SCHEMA) return@forEach
            val item = runCatching {
                FlashQueueDraftItem(
                    partition = decodeField(parts[1]),
                    sourceUri = decodeField(parts[2]),
                    displayName = decodeField(parts[3]),
                    expectedSizeBytes = parts[4].toLong(),
                    addedAtEpochMs = parts[5].toLong()
                )
            }.getOrNull() ?: return@forEach
            if (FlashOperationDraftPolicy.isPersistable(item)) items[item.partition] = item
        }
        return FlashOperationDraft(items.values.toList())
    }

    private fun encodeField(value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeField(value: String): String =
        decoder.decode(value).toString(Charsets.UTF_8)
}
