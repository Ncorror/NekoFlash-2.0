package ru.forum.adbfastboottool

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import androidx.core.widget.NestedScrollView

/** Keeps a restored reading position through dialog inset/size changes. */
class OperationCenterScrollView(context: Context, attrs: AttributeSet) : NestedScrollView(context, attrs) {
    private var restoredPosition: Int? = null
    private val restoreBeforeDraw = ViewTreeObserver.OnPreDrawListener {
        restoredPosition?.let { position ->
            val y = if (position == END) getChildAt(0).height else position
            scrollTo(0, y)
        }
        true
    }

    fun readingPosition(): Int = restoredPosition ?: if (scrollY > 0 && !canScrollVertically(1)) END else scrollY

    fun restoreReadingPosition(position: Int) {
        releaseRestoration()
        if (position == 0) {
            scrollTo(0, 0)
        } else {
            // An older Android dialog can lay out several times. Bottom is an
            // anchor, not the old window's maximum pixel offset.
            restoredPosition = position
            viewTreeObserver.addOnPreDrawListener(restoreBeforeDraw)
            invalidate()
        }
    }

    private fun releaseRestoration() {
        if (restoredPosition == null) return
        restoredPosition = null
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(restoreBeforeDraw)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) releaseRestoration()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) releaseRestoration()
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        releaseRestoration()
        return super.onGenericMotionEvent(event)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        releaseRestoration()
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        releaseRestoration()
        super.onDetachedFromWindow()
    }

    private companion object { const val END = -1 }
}
