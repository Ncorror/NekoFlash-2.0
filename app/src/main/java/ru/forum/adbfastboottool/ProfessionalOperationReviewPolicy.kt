package ru.forum.adbfastboottool

/**
 * A single contextual review for destructive GUI actions.
 *
 * This is a confirmation UI policy, NOT an authorization layer: expert terminal
 * ADB/Fastboot commands, custom partition names, raw protocol output, and the
 * existing operation executor remain completely available.
 */
internal object ProfessionalOperationReviewPolicy {
    enum class Action { ERASE, FORMAT, QUICK_FLASH, MASS_FLASH }

    fun requiresReview(action: Action, partitions: List<String>, slot: String? = null): Boolean =
        when (action) {
            Action.ERASE, Action.FORMAT -> true
            Action.QUICK_FLASH -> slot.equals("all", ignoreCase = true) ||
                partitions.any(::isCritical)
            Action.MASS_FLASH -> partitions.size > 1 || partitions.any(::isCritical)
        }

    fun isCritical(partition: String): Boolean =
        FastbootPartitionInventory.riskTier(partition) ==
            FastbootPartitionInventory.RiskTier.CRITICAL

    /** Use exact user-selected partition names in the confirmation, never an invented slot. */
    fun displayTarget(partition: String, slot: String?): String = when {
        slot.isNullOrBlank() -> partition
        partition.endsWith("_a", ignoreCase = true) || partition.endsWith("_b", ignoreCase = true) ->
            partition
        else -> "$partition  [slot: $slot]"
    }
}
