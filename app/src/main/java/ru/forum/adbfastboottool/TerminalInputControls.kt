package ru.forum.adbfastboottool

import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

internal enum class TerminalKey(val label: String, val description: Int, val wire: String) {
    UP("↑", R.string.terminal_lab_up, "\u001b[A"),
    DOWN("↓", R.string.terminal_lab_down, "\u001b[B"),
    LEFT("←", R.string.terminal_lab_left, "\u001b[D"),
    RIGHT("→", R.string.terminal_lab_right, "\u001b[C"),
    INTERRUPT("Ctrl+C", R.string.terminal_lab_interrupt, "\u0003"),
    TAB("Tab", R.string.terminal_lab_tab, "\t"),
    ESC("Esc", R.string.terminal_lab_escape, "\u001b"),
    SLASH("/", R.string.terminal_lab_slash, "/"),
    DASH("−", R.string.terminal_lab_dash, "-"),
    HOME("Home", R.string.terminal_lab_home, "\u001b[H"),
    END("End", R.string.terminal_lab_end, "\u001b[F"),
    PAGE_UP("PgUp", R.string.terminal_lab_pgup, "\u001b[5~"),
    PAGE_DOWN("PgDn", R.string.terminal_lab_pgdn, "\u001b[6~");

    companion object {
        fun navigation(code: Int): TerminalKey? = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> UP
            KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
            KeyEvent.KEYCODE_MOVE_HOME -> HOME
            KeyEvent.KEYCODE_MOVE_END -> END
            KeyEvent.KEYCODE_PAGE_UP -> PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> PAGE_DOWN
            KeyEvent.KEYCODE_ESCAPE -> ESC
            KeyEvent.KEYCODE_TAB -> TAB
            else -> null
        }
    }
}

/** Accessory buttons, hardware keys and Enter share the editor's HOST/PTY routing. */
internal class TerminalInputControls(
    private val activity: AppCompatActivity,
    private val input: PtyInputEditText,
    private val output: RecyclerView,
    private val history: (Int) -> Unit,
    private val interruptHost: () -> Unit,
    private val hideKeyboard: () -> Unit,
    submitHost: () -> Unit,
) {
    private val mode = activity.findViewById<TextView>(R.id.tvTerminalInputMode)
    private val prompt = activity.findViewById<TextView>(R.id.tvTerminalPrompt)
    private var displayedRaw: Boolean? = null

    init {
        input.bindTerminalActions(::editHost, submitHost)
        val row = activity.findViewById<LinearLayout>(R.id.terminalKeyButtons)
        TerminalKey.entries.forEach { key ->
            val button: MaterialButton = when (key) {
                TerminalKey.UP -> row.findViewById(R.id.btnHistoryUp)
                TerminalKey.DOWN -> row.findViewById(R.id.btnHistoryDown)
                else -> (activity.layoutInflater.inflate(R.layout.item_terminal_key, row, false) as MaterialButton)
                    .also { row.addView(it) }
            }
            button.text = key.label
            button.contentDescription = activity.getString(key.description)
            button.tag = key
            button.setOnClickListener { input.pressTerminalKey(key) }
        }
        activity.findViewById<View>(R.id.btnTerminalKeysMenu).setOnClickListener { anchor ->
            PopupMenu(activity, anchor).apply {
                TerminalKey.entries.forEach { key -> menu.add(0, key.ordinal + 1, key.ordinal, key.label) }
                setOnMenuItemClickListener { item -> input.pressTerminalKey(TerminalKey.entries[item.itemId - 1]) }
            }.show()
        }
        activity.findViewById<View>(R.id.btnSend).setOnClickListener { input.submitLine() }
        input.setOnEditorActionListener { _, actionId, event ->
            if (event == null && (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE)) input.submitLine()
            else false
        }
        refreshMode()
    }

    fun refreshMode() {
        val raw = input.isPtyInputActive()
        if (raw == displayedRaw) return
        displayedRaw = raw
        mode.setText(if (raw) R.string.terminal_lab_mode_pty else R.string.terminal_lab_mode_host)
        prompt.setText(if (raw) R.string.terminal_lab_raw_marker else R.string.terminal_lab_prompt)
        input.setHint(if (raw) R.string.terminal_lab_input_pty else R.string.layout_command_hint)
    }

    private fun editHost(key: TerminalKey): Boolean {
        val text = input.text ?: return false
        val first = minOf(input.selectionStart, input.selectionEnd).takeIf { it >= 0 } ?: text.length
        val last = maxOf(input.selectionStart, input.selectionEnd).takeIf { it >= 0 } ?: text.length
        when (key) {
            TerminalKey.UP -> history(-1)
            TerminalKey.DOWN -> history(1)
            TerminalKey.LEFT -> input.setSelection(if (first != last) first else TextUtils.getOffsetBefore(text, first))
            TerminalKey.RIGHT -> input.setSelection(if (first != last) last else TextUtils.getOffsetAfter(text, last))
            TerminalKey.HOME -> input.setSelection(0)
            TerminalKey.END -> input.setSelection(text.length)
            TerminalKey.PAGE_UP, TerminalKey.PAGE_DOWN -> {
                val page = (output.height - output.paddingTop - output.paddingBottom).coerceAtLeast(0)
                output.scrollBy(0, if (key == TerminalKey.PAGE_UP) -page else page)
            }
            TerminalKey.SLASH, TerminalKey.DASH -> {
                text.replace(first, last, key.wire)
                input.setSelection(first + key.wire.length)
            }
            TerminalKey.TAB -> completeHostWord(first, last)
            TerminalKey.ESC -> hideKeyboard()
            TerminalKey.INTERRUPT -> interruptHost()
        }
        return true
    }

    // Complete only a command or verb. Preserve arguments, suffix text and
    // quoted paths. Completion never submits a command.
    private fun completeHostWord(first: Int, last: Int) {
        val text = input.text ?: return
        if (first != last) return
        val before = text.subSequence(0, first).toString()
        val start = before.indexOfLast(Char::isWhitespace) + 1
        val context = before.substring(0, start).trim()
        val choices = when (context) {
            "" -> HOST_PROGRAMS
            "adb" -> ADB_VERBS
            "fastboot" -> FASTBOOT_VERBS
            else -> return
        }
        val prefix = before.substring(start)
        if (prefix.isEmpty()) return
        val matches = choices.filter { it.startsWith(prefix, ignoreCase = true) }
        if (matches.isEmpty()) return
        val word = matches.reduce { a, b -> a.commonPrefixWith(b) }
        if (matches.size > 1 && word.length <= prefix.length) return
        var end = first
        while (end < text.length && !text[end].isWhitespace()) end++
        val insert = word + if (matches.size == 1 && end == text.length) " " else ""
        text.replace(start, end, insert)
        input.setSelection(start + insert.length)
    }

    private companion object {
        val HOST_PROGRAMS = listOf("adb", "fastboot")
        val ADB_VERBS = listOf("devices", "shell", "exec", "reboot", "push", "pull", "install",
            "install-multiple", "logcat", "get-state", "getprop", "reports", "root", "unroot", "remount")
        val FASTBOOT_VERBS = listOf("devices", "getvar", "reboot", "boot", "flash", "erase",
            "format", "fetch", "set_active", "flashing", "oem", "is-logical", "reports")
    }
}
