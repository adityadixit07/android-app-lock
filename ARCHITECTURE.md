# Architecture

> **Keep this file current.** Any change that adds, removes or rewires a component, a
> permission, a data store or a security decision must update the relevant section here
> in the same commit.

## 1. Goals & non-goals

| Goals | Non-goals |
|---|---|
| Lock selected apps behind PIN, pattern or strong biometrics | Protect against root / ADB / custom-recovery attackers |
| Ad-free, tracker-free, **no `INTERNET` permission** | Hiding notification content of locked apps (OS controls it) |
| Reliable on Android 8 → 15 incl. OEM battery killers | Hiding locked apps' recents thumbnails (OS controls it) |
| Battery-neutral detection | Cloud backup of settings |
| Small, testable, auditable code base | |

Threat model: a person with **temporary physical access to an unlocked phone** (friend,
family member, child, shoulder-surfer). Everything below is designed for that adversary.

## 2. Layered structure (MVVM + Clean Architecture)

```
app/src/main/java/com/adityadixit/applock/
├── core/            Qualifiers, TimeSource                         (pure Kotlin)
├── domain/          Business rules — NO Android imports            (pure Kotlin, unit-tested)
│   ├── model/         InstalledApp, LockSettings, RelockPolicy, VerifyResult…
│   ├── repository/    Interfaces only
│   ├── auth/          Authenticator, LockoutPolicy, CredentialRules, PatternCodec
│   └── lock/          LockEngine (state machine), LockSessionTracker (grace periods)
├── data/            Implementations of domain interfaces
│   ├── prefs/         DataStore repositories, EncryptedCredentialRepository
│   ├── security/      CredentialHasher (PBKDF2), KeystoreCipher (AES-GCM, Keystore)
│   └── apps/          PackageManager app list, AppIconLoader (LRU cache)
├── system/          Thin wrappers over Android system state: permissions, intents, biometrics
├── service/         Detection hosts + LockCoordinator + boot receiver
├── overlay/         System-window lock screen (ComposeView in WindowManager)
├── admin/           DeviceAdminReceiver (uninstall protection)
├── di/              Hilt modules
└── ui/              Compose screens + ViewModels
    ├── components/    Design-system pieces: AppIcon, IconBadge, SettingsGroup, FeatureRow, rememberLoaded
    ├── lock/          Shared LockScreen, PinPad, PatternLock, LockScreenController
    ├── gate/          PIN gate protecting App Lock itself
    ├── onboarding/    Welcome → create credential → permissions
    ├── credential/    Create / change PIN or pattern
    ├── permissions/   Permission checklist (onboarding + settings)
    ├── apps/          App selection list
    ├── settings/      Settings
    └── biometric/     BiometricUnlockActivity (BiometricPrompt over a locked app)
```

Dependency rule: `ui → domain ← data`, and `service/overlay → domain`. `domain` depends
on nothing Android-specific, so the security-critical decisions (who gets locked, when a
grace period expires, lockouts) run as plain JVM unit tests.

## 3. Runtime flow

```mermaid
flowchart LR
    subgraph Detection host  [one active at a time]
        A11y[AppLockAccessibilityService<br/>event-driven]
        FGS[UsageMonitorService<br/>FGS · polls UsageEvents]
    end
    A11y -- foreground pkg --> C[LockCoordinator]
    FGS  -- foreground pkg --> C
    C -- onForegroundAppChanged --> E[LockEngine<br/>@Singleton state machine]
    E -- StateFlow&lt;LockState&gt; --> C
    C -- Locked --> O[LockOverlayController<br/>full-screen system window]
    C -- AwaitingBiometric --> B[BiometricUnlockActivity]
    O -- PIN/pattern --> AU[Authenticator]
    AU --> CR[(EncryptedCredentialRepository)]
    O -- success --> E
    B -- success / dismissed --> E
    S[(DataStore: locked apps, settings)] --> E
```

### LockEngine state machine

```
          locked app in front, no valid session
  Idle ─────────────────────────────────────────▶ Locked(pkg, autoPrompt)
   ▲  ◀── unlock success / left app / config off ──┘   │ biometric requested
   │                                                    ▼
   └──── success ──── AwaitingBiometric(pkg, promptVisible=false→true)
                         │ dismissed / cancelled / home
                         └────────────▶ Locked(pkg, autoPrompt=false)
```

* **Fail closed:** sessions are in memory only, so process death or reboot re-locks
  everything. If the overlay window cannot be added, the coordinator sends the user home.
* **Never lock without a credential:** the engine only enforces when a credential exists,
  onboarding is complete and the master switch is on, so a user can't lock themselves out.
* **Transient packages** (System UI, the share sheet, permission dialogs, this app) never
  count as "leaving" the locked app.
* **Screen off** clears every session and arms the overlay before the screen comes back on.

### Re-lock policy (grace period)

`LockSessionTracker` records when the user leaves each unlocked app (monotonic
`elapsedRealtime`, immune to wall-clock changes). Returning within `RelockPolicy.graceMillis`
(0 / 1 min / 5 min) resumes the session; otherwise it is discarded.

## 4. Detection: AccessibilityService vs UsageStatsManager

Both are implemented behind the same `LockCoordinator` / `LockEngine`. `PermissionStatus.detectionMode()`
picks Accessibility when enabled, otherwise Usage Access + overlay. Only one host runs:
the accessibility service stops the FGS when it connects, and restarts it as a fallback when
it is unbound.

| | **AccessibilityService** (primary) | **UsageStatsManager + Foreground Service** (fallback) |
|---|---|---|
| Mechanism | System pushes `TYPE_WINDOW_STATE_CHANGED` | Poll `queryEvents()` every 350 ms (incremental window) |
| Latency | ~0–50 ms, usually before the target draws its first frame | Avg ~175 ms + event logging delay; content may flash briefly |
| Battery | Negligible (push, no wakeups) | Small IPC per poll **only while the screen is on**; zero when off |
| Permissions | Accessibility only — draws with `TYPE_ACCESSIBILITY_OVERLAY`, no overlay permission needed | Usage access + Display over other apps + notification |
| Survival | Bound by the system; survives Doze; some OEMs still kill it | `specialUse` FGS + battery-optimisation exemption; can be killed by aggressive OEMs |
| Background activity start | Exempt (system-bound service) | Allowed via `SYSTEM_ALERT_WINDOW` while our overlay is visible |
| Google Play | **Not permitted** for app locking (accessibility API policy) → F-Droid / GitHub releases | Allowed (needs `specialUse` FGS + Usage access declarations) |
| Android 13+ sideload | "Allow restricted settings" step needed | Not affected |
| Privacy | Configured with `canRetrieveWindowContent=false`: sees package/class names only | Sees package names of resumed activities only |

**False-positive filter (accessibility):** dialogs, keyboards, toasts and chat-head bubbles
also fire window-state events. The service only reacts when `packageName/className` resolves to
a real `Activity` (`PackageManager.getActivityInfo`, LRU-cached). Without this, a floating
bubble from another app could switch the foreground and hide the lock.

## 5. Lock overlay

`LockOverlayController` adds a `ComposeView` directly to `WindowManager`:

* Window type depends on the host: `TYPE_ACCESSIBILITY_OVERLAY` or `TYPE_APPLICATION_OVERLAY`.
* Opaque (`PixelFormat.OPAQUE`), full screen, draws behind system bars (`fitInsetsTypes = 0`)
  with content padded by `WindowInsets.safeDrawing`.
* Focusable, so it receives BACK. `BackInterceptingLayout` turns BACK into "go home"; nothing
  can dismiss the window except a successful unlock or leaving the app.
* `OverlayLifecycleOwner` supplies the Lifecycle + SavedStateRegistry that Compose needs
  outside an Activity.

**Biometrics** need a `FragmentActivity`, so `BiometricUnlockActivity` (opaque, own task,
excluded from recents) is launched over the locked app. The overlay stays up until the
activity reports its window is visible (`onBiometricPromptVisible`), so the protected app never
flashes in between. Every exit path (success, error, "Use PIN", home, screen off) reports back
to the engine exactly once.

## 6. Security design

| Concern | Decision |
|---|---|
| Credential at rest | Salted **PBKDF2-HMAC-SHA256** (120k iterations, 16-byte salt, 256-bit output) → record **AES-256-GCM encrypted** with a non-exportable **Android Keystore** key (StrongBox when available) → own DataStore file |
| Why not `security-crypto`? | `EncryptedSharedPreferences` is deprecated; we use the same primitive (Keystore AES-GCM) directly, with no extra dependency |
| Why PBKDF2, not Argon2? | Built into the platform since API 26, so no native library. Keystore wrapping already stops offline attacks on a dumped file |
| Comparison | `MessageDigest.isEqual` (constant time) |
| Secrets in memory | PIN digits live in `CharArray` buffers (never `String` or Compose state) and are wiped after use |
| Brute force | `LockoutPolicy`: 4 free attempts, then 30 s doubling to a 15 min cap. Persisted, and shared by every unlock surface. Capped so moving the clock back can't lock a user out forever |
| Biometrics | `BIOMETRIC_STRONG` (Class 3) only; weak face unlock is rejected |
| Management UI | Gated by the same credential; re-locks on every return from background (2 min grace only after we opened a system Settings screen for the user); `FLAG_SECURE` |
| Backups | `allowBackup=false` + data-extraction rules exclude everything from cloud backup **and** device transfer |
| Network | No `INTERNET` permission at all |
| Package visibility | `<queries>` for launcher/home intents + package installer. No `QUERY_ALL_PACKAGES` |
| Exported components | Only the launcher activity, plus system-bound components protected by `BIND_ACCESSIBILITY_SERVICE` / `BIND_DEVICE_ADMIN` |
| Self-protection | Optional device admin (no policies) blocks uninstall; optional locking of `com.android.settings` + package installer blocks disabling Accessibility, revoking permissions or uninstalling without the PIN |
| Unreadable credential | Keystore key lost → repository reports "no credential" → engine stops enforcing and the app routes to setup (fails open for the owner instead of bricking their apps) |

### Known limitations (documented, OS-imposed)

* **Safe mode** disables all third-party apps, including this one.
* Notification previews and recents thumbnails of locked apps are controlled by the OS.
* BiometricPrompt without a `CryptoObject` can be bypassed by an attacker with root + hooking
  frameworks, which is outside the threat model.
* Moving the wall clock forward shortens a lockout. Locking Settings mitigates this.

## 7. Data

| Store | File | Contents |
|---|---|---|
| `@AppPreferences` DataStore | `app_lock_settings.preferences_pb` | locked package set, master switch, relock policy, biometric flag, onboarding flag, failed attempts, lockout deadline |
| `@CredentialPreferences` DataStore | `app_lock_credential.preferences_pb` | credential type, Base64(AES-GCM(PBKDF2 record)) |
| Android Keystore | alias `app_lock_credential_key_v1` | AES-256 key, non-exportable |

DataStore was chosen over Room: the data is a small key/value set with no relational
queries. Room can be introduced behind `LockedAppsRepository` without touching callers (for
example, for per-app relock policies).

## 8. Android version handling

| API | Handling |
|---|---|
| 26–27 | AppCompat theme so `androidx.biometric`'s fingerprint dialog works |
| 28+ | StrongBox key attempt, display-cutout mode for overlay |
| 29+ | `ACTIVITY_RESUMED` usage events, `unsafeCheckOpNoThrow` |
| 30+ | `fitInsetsTypes = 0` on overlay, `ACTION_BIOMETRIC_ENROLL`, package visibility via `<queries>` |
| 31+ | `ForegroundServiceStartNotAllowedException` handled; data-extraction rules |
| 33+ | `POST_NOTIFICATIONS` runtime permission; "restricted settings" guidance for sideloaded APKs; `ResolveInfoFlags`/`ComponentInfoFlags` |
| 34+ | `foregroundServiceType="specialUse"` + `FOREGROUND_SERVICE_SPECIAL_USE` + subtype property |
| 35 | `specialUse` FGS may start from `BOOT_COMPLETED` (unlike `dataSync`); edge-to-edge enforced, handled by Compose insets |

## 9. Testing strategy

* **Unit (JVM):** `LockEngine`, `LockSessionTracker`, `Authenticator`, `LockoutPolicy`,
  `CredentialRules`, `CredentialHasher` — the security-critical logic. Fakes live in
  `app/src/test/.../testing/Fakes.kt`.
* **Manual device matrix:** see README → *Testing on a device*.
* **Next:** instrumented tests for `KeystoreCipher` and `EncryptedCredentialRepository`, and
  Compose UI tests for `LockScreen`.

## 10. Build & delivery

* `app/debug.keystore` is the **public** debug key (password `android`), committed on purpose so
  every CI build has the same signature and can be installed over the previous one. Release
  builds must use a private key supplied via CI secrets; never this one.
* CI (`.github/workflows/android.yml`): `build` (unit tests + debug APK artifact) → `publish-debug`
  replaces the `debug-latest` GitHub pre-release with the new APK. `lint` runs as a separate job
  so a lint finding never blocks a testable APK.

## 11. Extension points

* **Per-app settings** (own relock policy, own credential): add a Room table behind
  `LockedAppsRepository`. `LockEngine` already keys sessions per package.
* **Intruder selfie / failed-attempt log:** subscribe to `VerifyResult.Failure` in `Authenticator`.
* **Play Store flavour:** add a product flavour that drops the accessibility service from its
  manifest. The rest of the app already runs on Usage Access.
