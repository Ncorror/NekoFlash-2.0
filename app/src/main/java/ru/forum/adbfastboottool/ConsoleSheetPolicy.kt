package ru.forum.adbfastboottool

/** Pure navigation policy for the persistent Console Bottom Sheet. */
object ConsoleSheetPolicy {
    /**
     * Calculates the body height that is actually visible inside a full-height
     * persistent BottomSheet. Material moves the whole sheet down for collapsed
     * and half-expanded states, so bottom-anchored children must be constrained
     * to the visible slice instead of the sheet's full measured height.
     */
    fun visibleBodyHeight(
        rootHeightPx: Int,
        sheetTopPx: Int,
        chromeHeightPx: Int,
    ): Int = (
        rootHeightPx.coerceAtLeast(0) -
            sheetTopPx.coerceAtLeast(0) -
            chromeHeightPx.coerceAtLeast(0)
        ).coerceAtLeast(0)

    enum class StableState {
        COLLAPSED,
        HALF_EXPANDED,
        EXPANDED,
        TRANSIENT,
    }

}
