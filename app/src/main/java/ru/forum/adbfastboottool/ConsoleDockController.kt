package ru.forum.adbfastboottool

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
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
    private val onExpanded: () -> Unit = {},
) {
    private val root: View by lazy { activity.findViewById(R.id.rootCoordinator) }
    private val panel: View by lazy { activity.findViewById(R.id.consolePanel) }
    private val handle: View by lazy { activity.findViewById(R.id.consoleResizeHandle) }
    private val header: LinearLayout by lazy { activity.findViewById(R.id.consoleHeader) }
    private val body: LinearLayout by lazy { activity.findViewById(R.id.consoleBody) }
    private val actions: LinearLayout by lazy { activity.findViewById(R.id.terminalHeaderActions) }
    private val inputBar: LinearLayout by lazy { activity.findViewById(R.id.consoleInputBar) }
    private val status: LinearLayout by lazy { activity.findViewById(R.id.terminalOperationStatus) }
    private val keyStrip: View by lazy { activity.findViewById(R.id.terminalKeyStrip) }
    private val keysMenu: View by lazy { activity.findViewById(R.id.btnTerminalKeysMenu) }
    private val mode: View by lazy { activity.findViewById(R.id.tvTerminalInputMode) }
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
    private var bodyHeightUpdateQueued: Boolean = false

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
                        scheduleVisibleBodyHeightUpdate()
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        scheduleVisibleBodyHeightUpdate()
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
        val updateHeight = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            scheduleVisibleBodyHeightUpdate()
        }
        header.addOnLayoutChangeListener(updateHeight)
        status.addOnLayoutChangeListener(updateHeight)
        root.addOnLayoutChangeListener(updateHeight)

        renderState(behavior.state)
        scheduleVisibleBodyHeightUpdate()
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
            // Material can restore EXPANDED without dispatching a state-change
            // callback. Reconcile the header and renderer even on a no-op open.
            renderState(BottomSheetBehavior.STATE_EXPANDED)
            scheduleVisibleBodyHeightUpdate()
        } else {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** Called after Android restores CoordinatorLayout/BottomSheet view state. */
    fun restoreState(expanded: Boolean) {
        val target = if (expanded) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
        if (behavior.state == target) {
            renderState(target)
            scheduleVisibleBodyHeightUpdate()
        } else {
            behavior.state = target
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
        mode.visibility = if (isExpanded) View.VISIBLE else View.GONE
        header.contentDescription = activity.getString(
            if (isExpanded) R.string.console_collapse_action_desc else R.string.console_expand_action_desc,
        )
        if (!isExpanded) peek.text = compactPeekText()

        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            // Catch the RecyclerView up only when the user opens the terminal.
            // Offscreen bursts must not keep reformatting and notifying rows.
            onExpanded()
            focusPendingCommandInput()
        }
    }


    /**
     * Material keeps this sheet measured at full screen height and changes only
     * its top position. Without constraining the body to the currently visible
     * slice, the RecyclerView bottom and command bar stay below the window in
     * half-expanded state.
     */
    /**
     * One body remeasure per display frame even when BottomSheet callbacks,
     * root layout and IME insets arrive together. Height and bottom bar still
     * follow the actual current sheet top; no intermediate resize is discarded
     * from the next frame's observable state.
     */
    private fun scheduleVisibleBodyHeightUpdate() {
        if (bodyHeightUpdateQueued) return
        bodyHeightUpdateQueued = true
        panel.postOnAnimation {
            bodyHeightUpdateQueued = false
            if (!activity.isDestroyed && !activity.isFinishing) {
                updateVisibleBodyHeight()
            }
        }
    }

    private fun updateVisibleBodyHeight(bottomSheet: View = panel) {
        if (root.height <= 0) return
        // Use the expanded budget, not the transient sheet top during a slide.
        // A tall landscape IME may leave room for just a prompt and a few rows.
        // In a very short window the USB header can leave room for the editor
        // alone. Temporarily let the open terminal use that space for output.
        val usbHeight = activity.findViewById<View>(R.id.usbHeader).height
        if (usbHeight > 0) {
            val desiredOffset = if (isExpanded && root.height - usbHeight < dp(128)) 0 else usbHeight
            if (behavior.expandedOffset != desiredOffset) {
                behavior.expandedOffset = desiredOffset
                panel.requestLayout()
            }
        }
        val statusHeight = if (status.isVisible) maxOf(status.measuredHeight, status.minimumHeight) else 0
        val budget = root.height - behavior.expandedOffset - statusHeight
        val inlineActions = isExpanded && budget < dp(152)
        val stripVisible = isExpanded && budget >= dp(208)
        val target = if (inlineActions) inputBar else header
        if (actions.parent !== target) {
            (actions.parent as LinearLayout).removeView(actions)
            target.addView(actions)
        }
        val statusTarget = if (inlineActions) actions else body
        if (status.parent !== statusTarget) {
            (status.parent as LinearLayout).removeView(status)
            statusTarget.addView(status, 0, LinearLayout.LayoutParams(
                if (inlineActions) dp(48) + status.paddingStart + status.paddingEnd else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        header.visibility = if (inlineActions) View.GONE else View.VISIBLE
        handle.visibility = if (isExpanded && budget < dp(184)) View.GONE else View.VISIBLE
        keyStrip.visibility = if (stripVisible) View.VISIBLE else View.GONE
        keysMenu.visibility = if (isExpanded && !stripVisible) View.VISIBLE else View.GONE

        val chrome = (if (header.isVisible) header.height else 0) +
            (if (handle.isVisible) handle.height else 0)

        val targetHeight = ConsoleSheetPolicy.visibleBodyHeight(
            rootHeightPx = root.height,
            sheetTopPx = bottomSheet.top,
            chromeHeightPx = chrome,
        )
        val params = body.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.height == targetHeight && params.weight == 0f) return

        params.height = targetHeight
        params.weight = 0f
        body.layoutParams = params
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

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
                scheduleVisibleBodyHeightUpdate()
            }

            // Window insets report keyboard visibility; they do not get to
            // override the user's explicit Collapse action.

            // Do not consume or duplicate IME/system-bar insets. `adjustResize`
            // remains the single owner of the available window height.
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    /**
     * After submitting a HOST command, prefer a readable answer over keeping
     * an IME that leaves less than one transcript row in landscape. Never
     * dismiss the keyboard merely for rotating, typing or PTY output: this
     * fallback belongs exclusively to an explicit HOST submission.
     */
    fun revealSubmittedHostReply() {
        if (!isExpanded) return
        val keyboardShown = imeVisible ||
            ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val output = activity.findViewById<View>(R.id.rvConsoleOutput)
        val outputRoom = (output.height - output.paddingTop - output.paddingBottom).coerceAtLeast(0)
        // After orientation changes the IME visibility inset can lag behind
        // the actual 100px resized window. A focused editor is sufficient
        // evidence to attempt release on an explicit HOST submission.
        if (!keyboardShown && !commandInput.hasFocus()) return
        if (root.height >= dp(180) && outputRoom >= dp(72)) return
        hideCommandIme()
    }

    fun hideCommandIme() {
        focusCommandWhenExpanded = false
        behavior.isDraggable = false
        // Remove editor focus before asking the IME to hide. On some API 34
        // layouts a hide request against the CoordinatorLayout is ignored
        // while the keyboard still owns the focused EditText.
        root.isFocusableInTouchMode = true
        root.requestFocus()
        commandInput.clearFocus()
        WindowInsetsControllerCompat(activity.window, commandInput).hide(
            WindowInsetsCompat.Type.ime(),
        )
        // Use the focused editor's window token for the legacy IME path as
        // well (not the bottom sheet's token). No new keyboard show is issued.
        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(commandInput.windowToken, 0)
        imeVisible = false
        scheduleVisibleBodyHeightUpdate()
    }

    private companion object {
        const val HALF_EXPANDED_RATIO = 0.55f
    }
}
