package com.adityadixit.applock.domain.lock

import com.adityadixit.applock.domain.model.LockSettings
import com.adityadixit.applock.domain.model.RelockPolicy
import com.adityadixit.applock.testing.FakeCredentialRepository
import com.adityadixit.applock.testing.FakeLockedAppsRepository
import com.adityadixit.applock.testing.FakeSettingsRepository
import com.adityadixit.applock.testing.FakeTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockEngineTest {

    private val time = FakeTimeSource()
    private val lockedApps = FakeLockedAppsRepository(setOf(BANK, CHAT))
    private val settings = FakeSettingsRepository(LockSettings(onboardingCompleted = true))
    private val credentials = FakeCredentialRepository()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private val engine = LockEngine(
        lockedAppsRepository = lockedApps,
        settingsRepository = settings,
        credentialRepository = credentials,
        time = time,
        transientPackages = setOf(SELF, SYSTEM_UI),
        scope = scope,
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `opening a locked app locks it`() {
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Locked(BANK, autoPromptBiometric = false), engine.state.value)
    }

    @Test
    fun `unlocked apps are ignored`() {
        engine.onForegroundAppChanged(BROWSER)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `going home while locked hides the lock`() {
        engine.onForegroundAppChanged(BANK)
        engine.onForegroundAppChanged(LAUNCHER)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `unlock then leave relocks immediately by default`() {
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        assertEquals(LockState.Idle, engine.state.value)

        engine.onForegroundAppChanged(LAUNCHER)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Locked(BANK, false), engine.state.value)
    }

    @Test
    fun `activity changes inside an unlocked app do not relock`() {
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onForegroundAppChanged(BANK)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `notification shade and own windows do not end a session`() {
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onForegroundAppChanged(SYSTEM_UI)
        engine.onForegroundAppChanged(SELF)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `grace period allows returning within the window`() {
        settings.flow.value = settings.flow.value.copy(relockPolicy = RelockPolicy.AFTER_1_MINUTE)
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onForegroundAppChanged(LAUNCHER)
        time.advance(30_000)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `grace period expires`() {
        settings.flow.value = settings.flow.value.copy(relockPolicy = RelockPolicy.AFTER_1_MINUTE)
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onForegroundAppChanged(LAUNCHER)
        time.advance(61_000)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Locked(BANK, false), engine.state.value)
    }

    @Test
    fun `unlocking one app does not unlock another`() {
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onForegroundAppChanged(CHAT)
        assertEquals(LockState.Locked(CHAT, false), engine.state.value)
    }

    @Test
    fun `screen off ends all sessions and pre-arms the lock`() {
        settings.flow.value = settings.flow.value.copy(relockPolicy = RelockPolicy.AFTER_5_MINUTES)
        engine.onForegroundAppChanged(BANK)
        engine.onUnlockSucceeded(BANK)
        engine.onScreenOff()
        assertEquals(LockState.Locked(BANK, false), engine.state.value)
    }

    @Test
    fun `never locks without a credential`() {
        credentials.typeFlow.value = null
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `never locks before onboarding completes`() {
        settings.flow.value = LockSettings(onboardingCompleted = false)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `master switch releases an active lock`() {
        engine.onForegroundAppChanged(BANK)
        settings.flow.value = settings.flow.value.copy(lockingEnabled = false)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `locking the app currently in front locks it right away`() {
        engine.onForegroundAppChanged(BROWSER)
        lockedApps.flow.value = lockedApps.flow.value + BROWSER
        assertEquals(LockState.Locked(BROWSER, false), engine.state.value)
    }

    @Test
    fun `biometric flow success`() {
        settings.flow.value = settings.flow.value.copy(biometricEnabled = true)
        engine.onForegroundAppChanged(BANK)
        assertEquals(LockState.Locked(BANK, autoPromptBiometric = true), engine.state.value)

        engine.onBiometricRequested(BANK)
        assertEquals(LockState.AwaitingBiometric(BANK, promptVisible = false), engine.state.value)
        engine.onBiometricPromptVisible(BANK)
        assertEquals(LockState.AwaitingBiometric(BANK, promptVisible = true), engine.state.value)

        // Our own biometric activity in front must not disturb the flow.
        engine.onForegroundAppChanged(SELF)
        engine.onUnlockSucceeded(BANK)
        assertEquals(LockState.Idle, engine.state.value)
    }

    @Test
    fun `dismissed biometric falls back to PIN without re-prompting`() {
        settings.flow.value = settings.flow.value.copy(biometricEnabled = true)
        engine.onForegroundAppChanged(BANK)
        engine.onBiometricRequested(BANK)
        engine.onBiometricDismissed(BANK)
        assertEquals(LockState.Locked(BANK, autoPromptBiometric = false), engine.state.value)
    }

    @Test
    fun `stale biometric callbacks for another app are ignored`() {
        engine.onForegroundAppChanged(BANK)
        engine.onBiometricRequested(CHAT)
        engine.onBiometricDismissed(CHAT)
        assertEquals(LockState.Locked(BANK, false), engine.state.value)
    }

    private companion object {
        const val SELF = "com.adityadixit.applock"
        const val SYSTEM_UI = "com.android.systemui"
        const val LAUNCHER = "com.android.launcher"
        const val BANK = "com.example.bank"
        const val CHAT = "com.example.chat"
        const val BROWSER = "com.example.browser"
    }
}
