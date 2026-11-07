package ru.forum.adbfastboottool

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** A permanently attached editor follows the last output row. A long transcript
 * scrolls within the remaining viewport without recycling the input connection. */
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
        output.measure(rowWidth, MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        output.layout(0, 0, measuredWidth, output.measuredHeight)
        val y = output.measuredHeight
        prompt.layout(0, y, measuredWidth, y + prompt.measuredHeight)
    }
}
