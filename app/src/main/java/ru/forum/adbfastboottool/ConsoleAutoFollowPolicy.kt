package ru.forum.adbfastboottool

/**
 * Keep automatic console scrolling independent of text selection.
 *
 * A user selecting diagnostic output must not lose the selection when another
 * ADB command prints a line. Explicitly opening the terminal remains a user
 * command to show its latest output. Full raw logs and transcript retention
 * are unaffected by this display-only policy.
 */
internal object ConsoleAutoFollowPolicy {
    fun followOutput(
        explicitlyRequested: Boolean,
        currentlyAtBottom: Boolean,
        selectingText: Boolean
    ): Boolean = explicitlyRequested || (currentlyAtBottom && !selectingText)
}
