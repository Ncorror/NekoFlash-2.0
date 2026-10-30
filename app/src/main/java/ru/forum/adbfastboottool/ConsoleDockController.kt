package ru.forum.adbfastboottool

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Owns the persistent Console Bottom Sheet state.
 *
 * The Material behavior performs all vertical dragging. This controller only
 * coordinates stable states and compact-header presentation, keeping manual
 * height mutation and gesture handling out of [MainActivity].
 */
internal class ConsoleDockController(
    private val activity: AppCompatActivity,
    private val consoleOutput: RecyclerView,
    private val onExpanded: () -> Unit = {},
) {
    private val root: View by lazy { activity.findViewById(R.id.rootCoordinator) }
    private val panel: View by lazy { activity.findViewById(R.id.consolePanel) }
    private val handle: View by lazy { activity.findViewById(R.id.consoleResizeHandle) }
    private val header: View by lazy { activity.findViewById(R.id.consoleHeader) }
    private val body: View by lazy { activity.findViewById(R.id.consoleBody) }
    private val toggle: TextView by lazy { activity.findViewById(R.id.tvConsoleToggle) }
    private val peek: TextView by lazy { activity.findViewById(R.id.tvConsolePeek) }
    private val logsButton: View by lazy { activity.findViewById(R.id.btnConsoleLogs) }
    private val historyUp: View by lazy { activity.findViewById(R.id.btnHistoryUp) }
    private val historyDown: View by lazy { activity.findViewById(R.id.btnHistoryDown) }
    private val commandInput: EditText by lazy { activity.findViewById(R.id.etCommand) }

    private lateinit var behavior: BottomSheetBehavior<View>
    private var focusCommandWhenExpanded: Boolean = false
    private var imeVisible: Boolean = false
    private var lastPeekLine: String = ""

    /** True while the Console body is visible above its collapsed peek height. */
    var isExpanded: Boolean = false
        private set

    /** Binds the Material Bottom Sheet and compact-header interactions. */
    fun initialize() {
        behavior = BottomSheetBehavior.from(panel).apply {
            isFitToContents = false
            isHideable = false
            skipCollapsed = false
            isDraggable = false
            halfExpandedRatio = HALF_EXPANDED_RATIO
            // Leave the fixed USB panel accessible while the terminal owns the
            // entire remaining working area.
            expandedOffset = activity.resources.getDimensionPixelSize(
                R.dimen.usb_panel_height,
            )
            peekHeight = 0 // REV6: terminal is visible only after explicit open().
            saveFlags = BottomSheetBehavior.SAVE_ALL
            state = BottomSheetBehavior.STATE_COLLAPSED
            addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        renderState(newState)
                        bottomSheet.post { updateVisibleBodyHeight(bottomSheet) }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        updateVisibleBodyHeight(bottomSheet)
                    }
                },
            )
        }

        val toggleSheet = View.OnClickListener { toggleExpandedState() }
        header.setOnClickListener(toggleSheet)
        handle.setOnClickListener(toggleSheet)

        // Only explicit UI actions open the sheet. IME focus callbacks can
        // arrive after collapse or during Activity recreation; responding by
        // expanding again makes the Close button feel stuck.

        bindWindowInsets()
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val headerHeight = activity.findViewById<View>(R.id.usbHeader).height
            if (headerHeight > 0 && behavior.expandedOffset != headerHeight) {
                behavior.expandedOffset = headerHeight
            }
            updateVisibleBodyHeight()
        }

        renderState(behavior.state)
        root.post { updateVisibleBodyHeight() }
    }

    /** Updates the single-line preview shown while the Console is collapsed. */
    fun updatePeek(lastLine: String) {
        lastPeekLine = lastLine.trim()
        if (!isExpanded) peek.text = compactPeekText()
    }

    /** Opens the unified Console and optionally focuses its embedded command input. */
    fun open(requestCommandFocus: Boolean = false) {
        focusCommandWhenExpanded = requestCommandFocus
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            focusPendingCommandInput()
        } else {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** Expands the Console to its full-height state without opening the keyboard. */
    fun expand() {
        open(requestCommandFocus = false)
    }

    /** Returns the Console to its compact persistent header. */
    fun collapse() {
        // Drop focus even when insets report IME already hidden. This also
        // prevents a delayed IME visibility callback from re-opening the sheet.
        hideCommandIme()
        if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /**
     * Handles the Console-specific part of Back navigation.
     *
     * Priority is deliberately local and predictable: hide the IME first,
     * then close the terminal.
     * Returning false delegates the remaining Back action to [MainActivity].
     */
    fun handleBack(): Boolean {
        val rootInsets = ViewCompat.getRootWindowInsets(root)
        val imeOrCommandActive = imeVisible ||
            rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true ||
            commandInput.hasFocus()
        val stableState = when (behavior.state) {
            BottomSheetBehavior.STATE_EXPANDED -> ConsoleSheetPolicy.StableState.EXPANDED
            BottomSheetBehavior.STATE_HALF_EXPANDED -> ConsoleSheetPolicy.StableState.HALF_EXPANDED
            BottomSheetBehavior.STATE_COLLAPSED -> ConsoleSheetPolicy.StableState.COLLAPSED
            else -> ConsoleSheetPolicy.StableState.TRANSIENT
        }

        return when {
            imeOrCommandActive -> {
                hideCommandIme()
                true
            }
            stableState != ConsoleSheetPolicy.StableState.COLLAPSED -> {
                collapse()
                true
            }
            else -> false
        }
    }

    private fun compactPeekText(): String = lastPeekLine.ifBlank {
        activity.getString(R.string.console_peek_hint)
    }

    private fun toggleExpandedState() {
        when (behavior.state) {
            BottomSheetBehavior.STATE_EXPANDED,
            BottomSheetBehavior.STATE_HALF_EXPANDED -> collapse()

            else -> expand()
        }
    }

    private fun renderState(state: Int) {
        isExpanded = state != BottomSheetBehavior.STATE_COLLAPSED
        toggle.text = activity.getString(
            if (isExpanded) R.string.layout_icon_down else R.string.layout_icon_up,
        )
        logsButton.visibility = if (isExpanded) View.VISIBLE else View.GONE
        historyUp.visibility = if (isExpanded) View.VISIBLE else View.GONE
        historyDown.visibility = if (isExpanded) View.VISIBLE else View.GONE
        peek.visibility = if (isExpanded) View.GONE else View.VISIBLE
        header.contentDescription = activity.getString(
            if (isExpanded) R.string.console_collapse_action_desc else R.string.console_expand_action_desc,
        )
        if (!isExpanded) peek.text = compactPeekText()

        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            // Catch the RecyclerView up only when the user opens the terminal.
            // Offscreen bursts must not keep reformatting and notifying rows.
            onExpanded()
            scrollOutputToBottom()
            focusPendingCommandInput()
        }
    }


    /**
     * Material keeps this sheet measured at full screen height and changes only
     * its top position. Without constraining the body to the currently visible
     * slice, the RecyclerView bottom and command bar stay below the window in
     * half-expanded state.
     */
    private fun updateVisibleBodyHeight(bottomSheet: View = panel) {
        if (root.height <= 0 || handle.height <= 0 || header.height <= 0) return

        val targetHeight = ConsoleSheetPolicy.visibleBodyHeight(
            rootHeightPx = root.height,
            sheetTopPx = bottomSheet.top,
            chromeHeightPx = handle.height + header.height,
        )
        val params = body.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.height == targetHeight && params.weight == 0f) return

        params.height = targetHeight
        params.weight = 0f
        body.layoutParams = params
    }

    private fun scrollOutputToBottom() {
        val lastPosition = (consoleOutput.adapter?.itemCount ?: 0) - 1
        if (lastPosition < 0) return
        consoleOutput.post { consoleOutput.scrollToPosition(lastPosition) }
    }

    private fun focusPendingCommandInput() {
        if (!focusCommandWhenExpanded) return
        panel.post {
            if (!focusCommandWhenExpanded || behavior.state != BottomSheetBehavior.STATE_EXPANDED || !commandInput.isShown || !commandInput.isEnabled) {
                focusCommandWhenExpanded = false
                return@post
            }
            // Request IME against the actual editable view rather than the
            // CoordinatorLayout. Some Android 14 emulator keyboards ignore the
            // first request during the sheet's expansion animation.
            commandInput.requestFocus()
            WindowInsetsControllerCompat(activity.window, commandInput).show(
                WindowInsetsCompat.Type.ime(),
            )
            focusCommandWhenExpanded = false
            // One bounded retry, only while the user still has the terminal
            // open and the command editor focused. Never reopen after Close.
            commandInput.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed &&
                    behavior.state == BottomSheetBehavior.STATE_EXPANDED &&
                    commandInput.isShown && commandInput.hasFocus() &&
                    ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) != true
                ) {
                    WindowInsetsControllerCompat(activity.window, commandInput).show(
                        WindowInsetsCompat.Type.ime(),
                    )
                }
            }, 180L)
        }
    }

    /**
     * Observes IME state without consuming insets or applying a second keyboard
     * padding. MainActivity keeps `adjustResize`, so the system already resizes
     * the CoordinatorLayout and the bottom-pinned command bar stays above IME.
     */
    private fun bindWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val nextImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (nextImeVisible != imeVisible) {
                imeVisible = nextImeVisible
                behavior.isDraggable = false
                panel.requestLayout()
                panel.post { updateVisibleBodyHeight() }
            }

            // Window insets report keyboard visibility; they do not get to
            // override the user's explicit Collapse action.

            // Do not consume or duplicate IME/system-bar insets. `adjustResize`
            // remains the single owner of the available window height.
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun hideCommandIme() {
        focusCommandWhenExpanded = false
        WindowInsetsControllerCompat(activity.window, root).hide(
            WindowInsetsCompat.Type.ime(),
        )
        imeVisible = false
        behavior.isDraggable = false
        // clearFocus() alone may immediately hand focus back to the only
        // focusable EditText on Android 8 (observed in instrumented REV31 CI).
        // Give it an explicit, non-editable destination before collapsing.
        root.isFocusableInTouchMode = true
        root.requestFocus()
        commandInput.clearFocus()
    }

    private companion object {
        const val HALF_EXPANDED_RATIO = 0.55f
    }
}
