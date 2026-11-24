package ru.forum.adbfastboottool

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Append-oriented renderer for the unified Console output.
 *
 * Each log entry is an independent RecyclerView row, so Android only lays out
 * the visible portion of a long session. The adapter keeps stable IDs and
 * exposes narrow mutation methods used by MainActivity's batched renderer.
 */
internal class ConsoleLogAdapter(initialTextSizeSp: Float) : RecyclerView.Adapter<ConsoleLogAdapter.LineViewHolder>() {
    internal data class Item(
        val id: Long,
        val text: String,
        val tone: CompactLogRenderPolicy.Tone,
    )

    private val items = ArrayList<Item>()
    private var nextId = 1L
    var textSizeSp = initialTextSizeSp
        set(value) {
            val normalized = value.coerceIn(9f, 20f)
            if (field == normalized) return
            field = normalized
            if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size)
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_console_line, parent, false) as TextView
        return LineViewHolder(view)
    }

    override fun onBindViewHolder(holder: LineViewHolder, position: Int) {
        holder.bind(items[position], textSizeSp)
    }

    fun replaceAll(lines: List<String>) {
        val previousSize = items.size
        if (previousSize > 0) {
            items.clear()
            notifyItemRangeRemoved(0, previousSize)
        }
        if (lines.isNotEmpty()) {
            items.addAll(lines.map(::newItem))
            notifyItemRangeInserted(0, lines.size)
        }
    }

    fun removeFirst(count: Int) {
        val actual = count.coerceIn(0, items.size)
        if (actual == 0) return
        items.subList(0, actual).clear()
        notifyItemRangeRemoved(0, actual)
    }

    fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        val start = items.size
        items.addAll(lines.map(::newItem))
        notifyItemRangeInserted(start, lines.size)
    }


    private fun newItem(rawLine: String): Item {
        val formatted = CompactLogRenderPolicy.format(rawLine)
        return Item(
            id = nextId++,
            text = formatted.text,
            tone = formatted.tone,
        )
    }

    private fun renderAnsi(value: String, defaultColor: Int): CharSequence {
        val pattern = Regex("\\u001B\\[([0-9;]*)m")
        val matches = pattern.findAll(value).toList()
        if (matches.isEmpty()) return value
        val result = SpannableStringBuilder()
        var cursor = 0
        var activeColor = defaultColor
        var bold = false

        fun appendSegment(end: Int) {
            if (end <= cursor) return
            val start = result.length
            result.append(value, cursor, end)
            val finish = result.length
            if (activeColor != defaultColor) result.setSpan(
                ForegroundColorSpan(activeColor), start, finish, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (bold) result.setSpan(
                StyleSpan(Typeface.BOLD), start, finish, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            cursor = end
        }

        fun ansiColor(code: Int): Int? {
            val base = arrayOf(
                Color.rgb(40, 44, 52), Color.rgb(224, 88, 88), Color.rgb(80, 190, 120),
                Color.rgb(220, 180, 80), Color.rgb(90, 145, 230), Color.rgb(190, 110, 210),
                Color.rgb(80, 190, 205), Color.rgb(225, 230, 235),
            )
            val bright = arrayOf(
                Color.rgb(105, 110, 120), Color.rgb(255, 105, 105), Color.rgb(120, 235, 155),
                Color.rgb(255, 220, 115), Color.rgb(125, 175, 255), Color.rgb(225, 145, 240),
                Color.rgb(115, 225, 240), Color.WHITE,
            )
            return when (code) {
                in 30..37 -> base[code - 30]
                in 90..97 -> bright[code - 90]
                else -> null
            }
        }

        matches.forEach { match ->
            appendSegment(match.range.first)
            val codes = match.groupValues[1].ifEmpty { "0" }.split(';').mapNotNull { it.toIntOrNull() }
            codes.forEach { code ->
                when (code) {
                    0 -> { activeColor = defaultColor; bold = false }
                    1 -> bold = true
                    22 -> bold = false
                    39 -> activeColor = defaultColor
                    else -> ansiColor(code)?.let { activeColor = it }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < value.length) appendSegment(value.length)
        return result
    }

    internal inner class LineViewHolder(
        private val textView: TextView,
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(item: Item, textSizeSp: Float) {
            val isWelcome = TerminalWelcomeMessage.isWelcomeLine(item.text)
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                if (isWelcome) TerminalWelcomeMessage.TEXT_SIZE_SP else textSizeSp,
            )
            textView.setLineSpacing(0f, 1f)
            textView.setSingleLine(isWelcome)
            textView.maxLines = if (isWelcome) 1 else Int.MAX_VALUE
            textView.ellipsize = if (isWelcome) TextUtils.TruncateAt.END else null
            val defaultColor = ContextCompat.getColor(textView.context, item.tone.colorResource())
            if (!isWelcome) {
                textView.text = renderAnsi(item.text, defaultColor)
                textView.setTextColor(defaultColor)
                return
            }

            val line = item.text
            val styled = SpannableString(line)
            fun color(start: Int, end: Int, colorRes: Int) {
                if (start < end) styled.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(textView.context, colorRes)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            val frame = R.color.terminal_welcome_frame
            color(0, line.length, R.color.terminal_welcome_text)
            when {
                line.startsWith("╔") || line.startsWith("╚") || line.startsWith("╰") || line.startsWith("=") ->
                    color(0, line.length, frame)
                line.startsWith("╟") -> color(0, line.length, R.color.terminal_welcome_section)
                line.startsWith("║ ") -> {
                    color(0, 1, frame)
                    color(line.length - 1, line.length, frame)
                    val bodyStart = 2
                    val bodyEnd = (line.length - 2).coerceAtLeast(bodyStart)
                    val body = line.substring(bodyStart, bodyEnd).trimEnd()
                    val value = body.trimStart()
                    val bodyColor = when {
                        value.contains("Welcome to NekoFlash-2.0") -> R.color.terminal_welcome_title
                        value.contains("HOST + DEVICE TERMINAL") || value.contains("ХОСТ + УСТРОЙСТВО") -> R.color.terminal_welcome_cyan
                        value.startsWith("export NEKO_USE_DEVICE_BINARIES=1") -> R.color.terminal_welcome_enabled
                        value.startsWith("export NEKO_USE_DEVICE_BINARIES=0") -> R.color.terminal_welcome_reset
                        value.startsWith("💡") || value == "help" -> R.color.terminal_welcome_help
                        value.startsWith("🟢") || value.startsWith("↗") -> R.color.terminal_welcome_enabled
                        value.startsWith("🔴") || value.startsWith("  export NEKO_USE_DEVICE_BINARIES=0") -> R.color.terminal_welcome_reset
                        value.startsWith("↩") || value.startsWith("🔗") -> R.color.terminal_welcome_source
                        value.startsWith("📱") || value.startsWith("🧩") || value.startsWith("📁") ||
                            value.startsWith("🕒") || value.startsWith("📅") || value.startsWith("⌂") ||
                            value.startsWith("⚙") || value.startsWith("▣") -> R.color.terminal_welcome_source
                        value.startsWith("🔐") -> R.color.terminal_welcome_note
                        value.startsWith("🔄") -> R.color.terminal_welcome_cyan
                        else -> R.color.terminal_welcome_text
                    }
                    color(bodyStart, bodyEnd, bodyColor)
                    val separator = line.indexOf(" = ", bodyStart)
                    if (separator in (bodyStart + 1) until bodyEnd) {
                        color(bodyStart, separator + 1, R.color.terminal_welcome_label)
                    }
                    if (body.contains("Welcome to NekoFlash-2.0")) {
                        val brand = line.indexOf("NekoFlash-2.0", bodyStart)
                        if (brand >= 0) color(brand, brand + "NekoFlash-2.0".length, R.color.terminal_welcome_cyan)
                    }
                }
            }
            textView.text = styled
            textView.setTextColor(defaultColor)
        }
    }
}

@ColorRes
private fun CompactLogRenderPolicy.Tone.colorResource(): Int = when (this) {
    CompactLogRenderPolicy.Tone.ERROR -> R.color.log_error
    CompactLogRenderPolicy.Tone.HINT -> R.color.log_hint
    CompactLogRenderPolicy.Tone.SUCCESS -> R.color.log_success
    CompactLogRenderPolicy.Tone.COMMAND -> R.color.log_command
    CompactLogRenderPolicy.Tone.WARNING -> R.color.log_warning
    CompactLogRenderPolicy.Tone.SYSTEM -> R.color.log_system
    CompactLogRenderPolicy.Tone.INFO -> R.color.log_info
    CompactLogRenderPolicy.Tone.LOCAL_OUTPUT -> R.color.log_local_output
    CompactLogRenderPolicy.Tone.TRANSPORT -> R.color.log_transport
    CompactLogRenderPolicy.Tone.FASTBOOT -> R.color.log_fastboot
    CompactLogRenderPolicy.Tone.FILE -> R.color.log_file
    CompactLogRenderPolicy.Tone.DATA -> R.color.log_data
    CompactLogRenderPolicy.Tone.OPERATION -> R.color.log_operation
    CompactLogRenderPolicy.Tone.INVENTORY -> R.color.log_inventory
}
