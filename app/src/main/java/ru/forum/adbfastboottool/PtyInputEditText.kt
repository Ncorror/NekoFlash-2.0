package ru.forum.adbfastboottool

import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import java.nio.charset.StandardCharsets

/**
 * One input field, two contracts:
 * - HOST CLI: normal editable line + Send/IME action.
 * - explicit adb shell PTY: committed IME bytes are forwarded immediately.
 *
 * Raw PTY input is never echoed into diagnostic logs by this widget, so typing
 * a password in a remote shell does not create a host-side plaintext log entry.
 */
internal class PtyInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var rawMode: () -> Boolean = { false }
    private var sink: (ByteArray) -> Boolean = { false }
    // Shared by the view's InputConnection wrappers and the paste menu.
    private var composing: String = ""
    private var previousRawMode: Boolean? = null
    private var hostKey: ((TerminalKey) -> Boolean)? = null
    private var hostSubmit: (() -> Unit)? = null
    private val handledKeyDowns = mutableSetOf<Int>()

    fun bindTerminalActions(keys: (TerminalKey) -> Boolean, submit: () -> Unit) {
        hostKey = keys
        hostSubmit = submit
    }

    fun isPtyInputActive(): Boolean = raw()

    fun submitLine(): Boolean {
        if (raw()) return finishRaw(byteArrayOf('\n'.code.toByte()))
        val submit = hostSubmit ?: return false
        submit()
        return true
    }

    fun pressTerminalKey(key: TerminalKey): Boolean =
        if (raw()) finishRaw(key.wire.toByteArray(StandardCharsets.UTF_8)) else hostKey?.invoke(key) ?: false

    fun configurePty(rawMode: () -> Boolean, sink: (ByteArray) -> Boolean) {
        this.rawMode = rawMode
        this.sink = sink
        composing = ""
        previousRawMode = null
    }

    private fun raw(): Boolean {
        val current = runCatching(rawMode).getOrDefault(false)
        if (previousRawMode != current) {
            composing = ""
            previousRawMode = current
        }
        return current
    }

    private fun emit(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        return runCatching { sink(bytes) }.getOrDefault(false)
    }

    private fun emitText(text: CharSequence?): Boolean =
        emit(text?.toString().orEmpty().toByteArray(StandardCharsets.UTF_8))

    private fun finishRaw(bytes: ByteArray): Boolean {
        val accepted = emit(bytes)
        if (accepted) composing = ""
        return accepted
    }

    private fun deletePtyText(beforeLength: Int, afterLength: Int, inCodePoints: Boolean): Boolean {
        if (beforeLength < 0 || afterLength < 0) return false
        val planned = PtyInputDelta.deletion(composing, beforeLength, afterLength, inCodePoints)
        val accepted = emit(planned.bytes)
        if (accepted) composing = planned.remainingComposition
        return accepted
    }

    // Physical keys arrive at the View. Some IMEs send the same kind of key
    // through InputConnection. Handle a key in exactly one place on either path.
    private fun terminalEvent(event: KeyEvent): Boolean? {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return if (handledKeyDowns.remove(code)) true else null
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val pty = raw()
        val enter = code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val navigation = TerminalKey.navigation(code)
        val result: Boolean = when {
            enter && (pty || event.hasNoModifiers() && hostSubmit != null) ->
                if (event.repeatCount == 0) submitLine() else true
            pty && code == KeyEvent.KEYCODE_DEL -> deletePtyText(1, 0, true)
            pty && event.isCtrlPressed && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                finishRaw(byteArrayOf((code - KeyEvent.KEYCODE_A + 1).toByte()))
            !pty && event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed &&
                code == KeyEvent.KEYCODE_C && !hasSelection() && hostKey != null ->
                pressTerminalKey(TerminalKey.INTERRUPT)
            event.hasNoModifiers() && (pty || hostKey != null) && navigation != null ->
                pressTerminalKey(navigation)
            pty -> {
                val unicode = if (event.isAltPressed) event.getUnicodeChar(
                    event.metaState and KeyEvent.META_ALT_MASK.inv()
                ) else event.unicodeChar
                if (unicode == 0 || !Character.isValidCodePoint(unicode)) return null
                val bytes = String(Character.toChars(unicode)).toByteArray(StandardCharsets.UTF_8)
                finishRaw(if (event.isAltPressed) byteArrayOf(27) + bytes else bytes)
            }
            else -> return null
        }
        handledKeyDowns.add(code)
        return result
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = terminalEvent(event) ?: super.dispatchKeyEvent(event)

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        // Android keyboards may replace a landscape EditText with a fullscreen
        // extracted editor, which steals the terminal's visible input and Send
        // controls. Keep command editing in this same inline widget for both
        // host CLI and explicit remote PTY input.
        outAttrs.imeOptions = outAttrs.imeOptions or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : InputConnectionWrapper(base, true) {

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.setComposingText(text, newCursorPosition)
                }
                val next = text?.toString().orEmpty()
                val ok = emit(PtyInputDelta.rewrite(composing, next))
                if (ok) composing = next
                return ok
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.commitText(text, newCursorPosition)
                }
                val next = text?.toString().orEmpty()
                val bytes = if (composing.isEmpty()) {
                    next.toByteArray(StandardCharsets.UTF_8)
                } else {
                    PtyInputDelta.rewrite(composing, next)
                }
                val ok = emit(bytes)
                if (ok) composing = ""
                return ok
            }

            override fun finishComposingText(): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.finishComposingText()
                }
                composing = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }
                return deletePtyText(beforeLength, afterLength, inCodePoints = false)
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }
                return deletePtyText(beforeLength, afterLength, inCodePoints = true)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.performEditorAction(actionCode)
                }
                val ok = emit(byteArrayOf('\n'.code.toByte()))
                if (ok) composing = ""
                return ok
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean =
                terminalEvent(event) ?: super.sendKeyEvent(event)
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (raw() && (id == android.R.id.paste || id == android.R.id.pasteAsPlainText)) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)
            if (text == null || !emitText(text)) return false
            composing = ""
            return true
        }
        return super.onTextContextMenuItem(id)
    }
}
