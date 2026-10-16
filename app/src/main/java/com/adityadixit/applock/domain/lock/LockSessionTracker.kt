package com.adityadixit.applock.domain.lock

import com.adityadixit.applock.domain.model.RelockPolicy

/**
 * Remembers which locked apps the user has unlocked and when they left them, and decides
 * whether returning to an app is still covered by the [RelockPolicy] grace period.
 *
 * Deliberately in-memory only: a process death or reboot re-locks everything (fail closed).
 * Not thread-safe; [LockEngine] guards access.
 */
class LockSessionTracker(private val now: () -> Long) {

    /** packageName -> time the user left it (null while it is still in the foreground). */
    private val sessions = HashMap<String, Long?>()

    fun onUnlocked(packageName: String) {
        sessions[packageName] = null
    }

    fun onLeft(packageName: String) {
        if (sessions.containsKey(packageName) && sessions[packageName] == null) {
            sessions[packageName] = now()
        }
    }

    /**
     * @return true if [packageName] may be shown without re-authenticating. A valid session that
     * is resumed is marked as in-foreground again; an expired one is discarded.
     */
    fun resumeSessionIfValid(packageName: String, policy: RelockPolicy): Boolean {
        if (!sessions.containsKey(packageName)) return false
        val leftAt = sessions[packageName] ?: return true
        val valid = policy.graceMillis > 0 && now() - leftAt <= policy.graceMillis
        if (valid) sessions[packageName] = null else sessions.remove(packageName)
        return valid
    }

    fun clearAll() = sessions.clear()
}
