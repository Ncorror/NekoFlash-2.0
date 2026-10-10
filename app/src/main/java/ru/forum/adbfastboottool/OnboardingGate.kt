package ru.forum.adbfastboottool

import android.content.Context
import androidx.core.content.edit

/**
 * Versioned risk acknowledgement plus a process/task scoped entry session.
 *
 * The persisted acknowledgement avoids forcing the user to re-read the risk
 * text after every launch, while [sessionAuthorized] deliberately lives only
 * in memory. A cold process start therefore always opens WelcomeActivity.
 * MainActivity also ends the session when its task is actually finished, so a
 * normal close/remove-from-recents launch returns through the welcome screen.
 *
 * Intent extras are never trusted as a bypass: only the in-memory session
 * opened by WelcomeActivity permits entry to MainActivity.
 */
object OnboardingGate {
    private const val PREFS_NAME = "nekoflash_onboarding"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val KEY_RISK_ACCEPTED = "risk_accepted"
    private const val CURRENT_SCHEMA_VERSION = 1

    @Volatile
    private var sessionAuthorized = false

    fun isCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SCHEMA_VERSION, 0) == CURRENT_SCHEMA_VERSION &&
            prefs.getBoolean(KEY_RISK_ACCEPTED, false)
    }
    /**
     * A cold process still enters through Welcome; permission/risk decisions
     * cannot become an artificial app-entry barrier. USB write commands retain
     * their own transport and file validation.
     */
    fun canEnterMain(context: Context): Boolean = sessionAuthorized

    fun complete(context: Context): Boolean {
        // Only WelcomeActivity invokes this after the user taps Enter.
        // No permanent consent or all-files access is demanded just to view UI.
        sessionAuthorized = true
        return true
    }

    /** Ends only the current app-entry session; the risk checkbox remains saved. */
    fun endSession() {
        sessionAuthorized = false
    }

    fun reset(context: Context) {
        endSession()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
