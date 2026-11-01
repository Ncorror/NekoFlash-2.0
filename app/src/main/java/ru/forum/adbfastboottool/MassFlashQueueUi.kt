package ru.forum.adbfastboottool

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.text.format.Formatter
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/** Presentation only. The ViewModel keeps the authoritative order and execution snapshot. */
internal class MassFlashQueueUi(
    activity: Activity,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit
) {
    // Inflation asks the RecyclerView parent for LayoutParams immediately.
    private val list: RecyclerView = activity.findViewById<RecyclerView>(R.id.rvMassQueue).apply {
        layoutManager = LinearLayoutManager(activity)
    }
    val header: View = activity.layoutInflater.inflate(R.layout.view_mass_flash_header, list, false)
    val execute: MaterialButton = activity.findViewById(R.id.btnMassExecute)
    private val heading: TextView = header.findViewById(R.id.tvMassQueueTitle)
    private val empty: View = header.findViewById(R.id.tvMassQueueEmpty)
    private val sourcesText: TextView = header.findViewById(R.id.tvMassSourceStatus)
    private var moveGeneration = 0L
    private var pendingReveal: Pair<Long, String>? = null
    private val rows = FlashQueueRowsAdapter({ partition, direction ->
        pendingReveal = ++moveGeneration to partition
        onMove(partition, direction)
    }, onRemove)
    private var shownItems: List<FlashQueueDraftItem>? = null

    init {
        // ConcatAdapter always has one header item, even while the asynchronous
        // ListAdapter has no rows. Without gating on the ROW adapter, the
        // RecyclerView can consume a saved deep scroll position against only
        // the header during Activity recreation, then jump to the top.
        rows.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        // This form can be recycled offscreen. Save it explicitly, even when detached.
        header.isSaveFromParentEnabled = false
        list.apply {
            adapter = ConcatAdapter(HeaderAdapter(header), rows)
            itemAnimator = null
            setHasFixedSize(true)
        }
    }

    fun render(draft: FlashOperationDraft, sources: QueueSourceState) {
        if (shownItems !== draft.items) {
            shownItems = draft.items
            val reveal = pendingReveal
            rows.submitList(draft.items.mapIndexed { index, item ->
                FlashQueueRow(item, index + 1, index == draft.items.lastIndex)
            }) {
                if (reveal != null && pendingReveal == reveal) {
                    pendingReveal = null
                    val index = rows.currentList.indexOfFirst { it.item.partition == reveal.second }
                    // Keep the moved row visible when RecyclerView preserves another row's anchor.
                    if (index >= 0) list.scrollToPosition(index + 1) // form header occupies position 0
                }
            }
        }
        val context = heading.context
        heading.text = if (draft.isEmpty) context.getString(R.string.flash_rev7_queue_empty)
            else context.getString(R.string.flash_rev7_queue_count, draft.items.size)
        empty.isVisible = draft.isEmpty && sources.pending.isEmpty()
        sourcesText.text = listOfNotNull(
            sources.pending.takeIf { it.isNotEmpty() }?.let {
                context.getString(R.string.rev24_queue_checking, it.joinToString())
            }, sources.error
        ).joinToString("\n")
        sourcesText.isVisible = sourcesText.text.isNotEmpty()
        execute.isEnabled = draft.items.isNotEmpty() && sources.pending.isEmpty()
        execute.text = if (draft.isEmpty) context.getString(R.string.flash_rev7_flash_queue)
            else context.resources.getQuantityString(R.plurals.rev25_flash_queue, draft.items.size, draft.items.size)
    }

    fun saveFormState(): Bundle = Bundle().apply {
        val state = SparseArray<Parcelable>()
        header.saveHierarchyState(state)
        putSparseParcelableArray("form", state)
    }

    @Suppress("DEPRECATION")
    fun restoreFormState(state: Bundle?) {
        state?.getSparseParcelableArray<Parcelable>("form")?.let(header::restoreHierarchyState)
    }

    private class HeaderAdapter(private val header: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(header) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
}

internal data class FlashQueueRow(val item: FlashQueueDraftItem, val ordinal: Int, val last: Boolean)

/** Diff identity is the exact partition; clicks resolve the current local adapter position. */
internal class FlashQueueRowsAdapter(
    private val onMove: (String, Int) -> Unit,
    private val onRemove: (String) -> Unit
) : ListAdapter<FlashQueueRow, FlashQueueRowsAdapter.Holder>(object : DiffUtil.ItemCallback<FlashQueueRow>() {
    override fun areItemsTheSame(old: FlashQueueRow, new: FlashQueueRow) = old.item.partition == new.item.partition
    override fun areContentsTheSame(old: FlashQueueRow, new: FlashQueueRow) = old == new
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_flash_queue, parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val target: TextView = view.findViewById(R.id.tvQueuePartition)
        private val image: TextView = view.findViewById(R.id.tvQueueImage)
        private val size: TextView = view.findViewById(R.id.tvQueueSize)
        private val up: MaterialButton = view.findViewById(R.id.btnQueueUp)
        private val down: MaterialButton = view.findViewById(R.id.btnQueueDown)
        private val remove: MaterialButton = view.findViewById(R.id.btnQueueRemove)
        init {
            fun current(): FlashQueueRow? = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?.let { currentList.getOrNull(it) }
            up.setOnClickListener { current()?.takeIf { it.ordinal > 1 }?.let { onMove(it.item.partition, -1) } }
            down.setOnClickListener { current()?.takeUnless { it.last }?.let { onMove(it.item.partition, 1) } }
            remove.setOnClickListener { current()?.let { onRemove(it.item.partition) } }
        }
        fun bind(row: FlashQueueRow) {
            val context = itemView.context
            target.text = context.getString(R.string.rev25_queue_target, row.ordinal, row.item.partition)
            image.text = row.item.displayName
            size.text = Formatter.formatShortFileSize(context, row.item.expectedSizeBytes)
            up.isEnabled = row.ordinal > 1
            down.isEnabled = !row.last
            listOf(up to R.string.flash_rev7_row_up, down to R.string.flash_rev7_row_down,
                remove to R.string.flash_rev7_row_remove).forEach { (button, label) ->
                button.contentDescription = context.getString(label) + ": " + row.item.partition
            }
        }
    }
}
