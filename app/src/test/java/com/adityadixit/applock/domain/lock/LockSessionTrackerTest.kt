package com.adityadixit.applock.domain.lock

import com.adityadixit.applock.domain.model.RelockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSessionTrackerTest {

    private var now = 0L
    private val tracker = LockSessionTracker { now }

    @Test
    fun `no session means locked`() {
        assertFalse(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_5_MINUTES))
    }

    @Test
    fun `session stays valid while app is in foreground`() {
        tracker.onUnlocked("a")
        now += 60 * 60_000L
        assertTrue(tracker.resumeSessionIfValid("a", RelockPolicy.IMMEDIATELY))
    }

    @Test
    fun `immediate policy relocks as soon as the app is left`() {
        tracker.onUnlocked("a")
        tracker.onLeft("a")
        assertFalse(tracker.resumeSessionIfValid("a", RelockPolicy.IMMEDIATELY))
    }

    @Test
    fun `grace period allows quick return`() {
        tracker.onUnlocked("a")
        tracker.onLeft("a")
        now += 59_000L
        assertTrue(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_1_MINUTE))
    }

    @Test
    fun `grace period expires`() {
        tracker.onUnlocked("a")
        tracker.onLeft("a")
        now += 61_000L
        assertFalse(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_1_MINUTE))
        // Expired session is discarded, not revived by a later check.
        now -= 30_000L
        assertFalse(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_1_MINUTE))
    }

    @Test
    fun `grace window restarts from the latest exit`() {
        tracker.onUnlocked("a")
        tracker.onLeft("a")
        now += 50_000L
        assertTrue(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_1_MINUTE))
        now += 50_000L
        tracker.onLeft("a")
        now += 50_000L
        assertTrue(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_1_MINUTE))
    }

    @Test
    fun `clearAll ends every session`() {
        tracker.onUnlocked("a")
        tracker.onUnlocked("b")
        tracker.clearAll()
        assertFalse(tracker.resumeSessionIfValid("a", RelockPolicy.AFTER_5_MINUTES))
        assertFalse(tracker.resumeSessionIfValid("b", RelockPolicy.AFTER_5_MINUTES))
    }
}
