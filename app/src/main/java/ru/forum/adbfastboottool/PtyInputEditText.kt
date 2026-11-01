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

    private fun emitDelete(count: Int): Boolean {
        if (count <= 0) return true
        return emit(ByteArray(count) { PtyInputDelta.DEL.toByte() })
    }

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

            private fun deletePtyText(
                beforeLength: Int, afterLength: Int, inCodePoints: Boolean
            ): Boolean {
                if (beforeLength < 0 || afterLength < 0) return false
                val planned = PtyInputDelta.deletion(
                    composing, beforeLength, afterLength, inCodePoints
                )
                val ok = emit(planned.bytes)
                // Do not advance composition when the PTY session rejected
                // this write: the next IME update must not lose input.
                if (ok) composing = planned.remainingComposition
                return ok
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

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (!raw()) {
                    composing = ""
                    return super.sendKeyEvent(event)
                }
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return super.sendKeyEvent(event)
                }
                val accepted = when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> deletePtyText(1, 0, inCodePoints = true)
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
                // Enter, navigation and hardware text terminate composing
                // input. Backspace is the exception: its prefix survives.
                if (accepted && event.keyCode != KeyEvent.KEYCODE_DEL) composing = ""
                return accepted
            }
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
