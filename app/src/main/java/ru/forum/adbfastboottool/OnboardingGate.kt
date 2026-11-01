package ru.forum.adbfastboottool

/**
 * Process/task-scoped Welcome entry gate.
 *
 * No permission, checkbox or persisted acknowledgement can become an artificial
 * barrier to viewing NekoFlash. A cold process still enters through Welcome,
 * and exported USB attach intents cannot bypass this in-memory session.
 */
object OnboardingGate {
    @Volatile
    private var sessionAuthorized = false

    fun canEnterMain(): Boolean = sessionAuthorized

    fun complete() {
        sessionAuthorized = true
    }

    fun endSession() {
        sessionAuthorized = false
    }
}
