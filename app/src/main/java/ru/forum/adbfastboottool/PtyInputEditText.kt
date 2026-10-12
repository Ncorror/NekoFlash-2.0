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

    fun configurePty(rawMode: () -> Boolean, sink: (ByteArray) -> Boolean) {
        this.rawMode = rawMode
        this.sink = sink
    }

    private fun raw(): Boolean = runCatching(rawMode).getOrDefault(false)

    private fun emit(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        return runCatching { sink(bytes) }.getOrDefault(false)
    }

    private fun emitText(text: CharSequence?): Boolean =
        emit(text?.toString().orEmpty().toByteArray(StandardCharsets.UTF_8))

    private fun emitDelete(count: Int): Boolean {
        if (count <= 0) return true
        return emit(ByteArray(count) { PtyInputDelta.DEL.toByte() })
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            private var composing = ""

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!raw()) return super.setComposingText(text, newCursorPosition)
                val next = text?.toString().orEmpty()
                val ok = emit(PtyInputDelta.rewrite(composing, next))
                if (ok) composing = next
                return ok
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!raw()) return super.commitText(text, newCursorPosition)
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
                if (!raw()) return super.finishComposingText()
                composing = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (!raw()) return super.deleteSurroundingText(beforeLength, afterLength)
                composing = ""
                return when {
                    beforeLength > 0 -> emitDelete(beforeLength)
                    afterLength > 0 -> emit("\u001B[3~".repeat(afterLength).toByteArray())
                    else -> true
                }
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                if (!raw()) {
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }
                composing = ""
                return when {
                    beforeLength > 0 -> emitDelete(beforeLength)
                    afterLength > 0 -> emit("\u001B[3~".repeat(afterLength).toByteArray())
                    else -> true
                }
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                if (!raw()) return super.performEditorAction(actionCode)
                composing = ""
                return emit(byteArrayOf('\n'.code.toByte()))
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (!raw() || event.action != KeyEvent.ACTION_DOWN) {
                    return super.sendKeyEvent(event)
                }
                composing = ""
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> emitDelete(1)
                    KeyEvent.KEYCODE_ENTER -> emit(byteArrayOf('\n'.code.toByte()))
                    KeyEvent.KEYCODE_TAB -> emit(byteArrayOf('\t'.code.toByte()))
                    KeyEvent.KEYCODE_DPAD_UP -> emit("\u001B[A".toByteArray())
                    KeyEvent.KEYCODE_DPAD_DOWN -> emit("\u001B[B".toByteArray())
                    KeyEvent.KEYCODE_DPAD_RIGHT -> emit("\u001B[C".toByteArray())
                    KeyEvent.KEYCODE_DPAD_LEFT -> emit("\u001B[D".toByteArray())
                    else -> {
                        val unicode = event.unicodeChar
                        if (unicode == 0) super.sendKeyEvent(event)
                        else emit(String(Character.toChars(unicode)).toByteArray(StandardCharsets.UTF_8))
                    }
                }
            }
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (raw() && (id == android.R.id.paste || id == android.R.id.pasteAsPlainText)) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)
            return text != null && emitText(text)
        }
        return super.onTextContextMenuItem(id)
    }
}
