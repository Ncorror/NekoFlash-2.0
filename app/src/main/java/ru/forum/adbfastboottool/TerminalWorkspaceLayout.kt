package ru.forum.adbfastboottool

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Keep the editor attached while the transcript scrolls in a bounded viewport. */
class TerminalWorkspaceLayout(context: Context, attrs: AttributeSet) : ViewGroup(context, attrs) {
    private lateinit var output: RecyclerView
    private lateinit var prompt: View

    override fun onFinishInflate() {
        super.onFinishInflate()
        output = findViewById(R.id.rvConsoleOutput)
        prompt = findViewById(R.id.consoleInputBar)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val rowWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        prompt.measure(rowWidth, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        val room = (height - prompt.measuredHeight).coerceAtLeast(0)
        // RecyclerView must only lay out its rows when the final viewport is
        // known. Auto-measuring its contents also lays out the adapter and can
        // consume a pending scroll to the new reply during IME resize.
        output.measure(rowWidth, MeasureSpec.makeMeasureSpec(room, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val room = output.measuredHeight
        output.layout(0, 0, measuredWidth, room)
        // A short transcript leaves unused space in the list. Put the real
        // prompt there, directly after the last row. A full list reserves the
        // bottom line for the prompt at every scroll position.
        val lastIndex = (output.adapter?.itemCount ?: 0) - 1
        val lastRow = output.layoutManager?.findViewByPosition(lastIndex)
        val y = when {
            lastIndex < 0 -> output.paddingTop.coerceAtMost(room)
            lastRow != null && !output.canScrollVertically(-1) && !output.canScrollVertically(1) ->
                (lastRow.bottom + output.paddingBottom).coerceIn(0, room)
            else -> room
        }
        prompt.layout(0, y, measuredWidth, y + prompt.measuredHeight)
    }

    // Blank canvas below a short transcript belongs to the workspace click
    // listener, so a tap there can focus the inline editor and show the IME.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        (event.actionMasked == MotionEvent.ACTION_DOWN && event.y >= prompt.bottom) ||
            super.onInterceptTouchEvent(event)
}
