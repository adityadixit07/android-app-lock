# Android App Lock

A clean, **ad-free, open-source** app locker for Android, built with Kotlin, Jetpack Compose
and Material 3.

* 🔒 Lock any app with a **PIN**, **pattern** or **fingerprint/face** (Class 3 biometrics)
* ⚡ **Instant detection** with an AccessibilityService, or a battery-friendly **Usage Access** fallback
* ⏱️ Re-lock **immediately** or after a **1 / 5 minute grace period**; everything re-locks on screen off
* 🛡️ Brute-force lockout, Keystore-encrypted PBKDF2 credential, optional **uninstall protection**
* 🙈 **No `INTERNET` permission.** Never reads screen content. Nothing leaves your phone.

| | |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose · Material 3 (dynamic colour) |
| Architecture | MVVM + Clean Architecture, Hilt, Coroutines/Flow |
| Storage | Jetpack DataStore + Android Keystore |
| Min / Target SDK | 26 (Android 8.0) / 35 (Android 15) |

Read **[ARCHITECTURE.md](ARCHITECTURE.md)** for the design, the detection trade-offs and the security model.

---

## Build

Requirements: **Android Studio Ladybug (2024.2) or newer**, **JDK 17**, Android SDK 35.

```bash
git clone https://github.com/adityadixit07/android-app-lock.git
cd android-app-lock
./gradlew assembleStandardDebug      # APK → app/build/outputs/apk/standard/debug/
./gradlew assembleFullDebug          # with Accessibility detection
./gradlew testStandardDebugUnitTest  # JVM unit tests for the locking & auth logic
```

Or open the folder in Android Studio and press **Run ▶**.

---

## Setup on a device or emulator

### 1. Install

**Easiest — straight onto your phone:** open
[Releases › debug-latest](https://github.com/adityadixit07/android-app-lock/releases/tag/debug-latest)
in your phone's browser, download **AppLock-debug.apk** (the `standard` flavor) and tap it (allow "Install unknown apps"
for your browser when asked). CI rebuilds it on every push. Builds are signed with the committed
public debug key, so newer builds install **over** older ones without losing your settings.

> **"App blocked to protect your device" (Google Play Protect)?** That happens with
> **AppLock-full-debug.apk**: Play Protect blocks browser installs of apps that declare an
> Accessibility service. Use **AppLock-debug.apk** (Usage Access detection, installs normally), or
> install the full build from a computer with `adb install -r AppLock-full-debug.apk`.

| Flavor | APK | Detection |
|---|---|---|
| `standard` (default) | `AppLock-debug.apk` | Usage Access (~350 ms) |
| `full` | `AppLock-full-debug.apk` | Instant Accessibility, falls back to Usage Access |

**From a computer:**

```bash
./gradlew assembleStandardDebug   # or assembleFullDebug
adb install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
```

### 2. First-run onboarding (in the app)

1. **Welcome → Get started**
2. **Create a PIN (4–12 digits) or a pattern (≥ 4 dots)**, then confirm it.
3. **Permissions checklist.** Grant each item; status refreshes when you return:
   | Permission | Needed for | Where Android sends you |
   |---|---|---|
   | **Accessibility service** *(recommended)* | Instant, zero-battery detection | Settings › Accessibility › App Lock |
   | **Usage access** *(alternative)* | Polling detection if you skip Accessibility | Settings › Usage access |
   | **Display over other apps** | Lock screen in Usage-access mode | Settings › Display over other apps |
   | **Unrestricted battery** | Survive Doze / OEM battery savers | One-tap system dialog |
   | **Notifications** (Android 13+) | Silent "protection active" notice | Runtime dialog |
   | **Fingerprint / face** | Optional biometric unlock | Toggle shown if hardware is enrolled |
4. **Finish setup** is enabled as soon as one detection method works.
5. On the **Apps** tab, search and flip the switch next to each app you want to lock.

> **Accessibility switch greyed out (Android 13+)?** For APKs installed from a browser or
> file manager, Android hides the switch behind *restricted settings*. Open **App info ›
> ⋮ › Allow restricted settings**, then enable the service. The permission screen has a
> shortcut button. APKs installed with `adb install` are not affected.

### 3. Optional hardening (Settings tab)

* **Lock Settings & uninstaller.** Requires your PIN to open system Settings or the uninstall
  dialog, so the service can't be disabled behind your back.
* **Prevent uninstall.** Activates App Lock as a device admin (no admin policies used).

### Fast setup via ADB (testing only)

```bash
PKG=com.adityadixit.applock
# Usage access + overlay
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
# Accessibility (⚠ replaces any other enabled accessibility services on the device)
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.service.AppLockAccessibilityService
adb shell settings put secure accessibility_enabled 1
# Battery optimisation exemption + notifications
adb shell dumpsys deviceidle whitelist +$PKG
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
```

---

## Testing on a device

### Emulator setup

* Create a **Pixel 8, API 35 (Google APIs)** AVD. Also test an **API 26** image for the lower bound.
* **Biometrics:** Settings › Security › Fingerprint › add one; when asked to touch the sensor run
  `adb -e emu finger touch 1` (or Extended controls › Fingerprint › *Touch sensor*). Use the same
  command to authenticate at the prompt; `finger touch 2` simulates a wrong finger.

### Manual test matrix

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 1 | Basic lock | Lock *Chrome*, open it | Lock screen with Chrome's icon covers it immediately |
| 2 | Wrong PIN & lockout | Enter a wrong PIN 5× | "Attempts left" warning, then 30 s countdown; doubles on further failures |
| 3 | Unlock | Enter correct PIN | Chrome is usable; switching tabs inside Chrome doesn't re-lock |
| 4 | Immediate re-lock | Home, reopen Chrome | Locked again |
| 5 | Grace period | Settings › Re-lock › *After 1 minute*; leave & return within 60 s, then after 60 s | Open within, locked after |
| 6 | Notification shade | Unlock Chrome, pull down the shade, close it | Stays unlocked |
| 7 | Screen off | Unlock Chrome, press power, wake | Locked (all sessions end on screen off) |
| 8 | Back button | On the lock screen press Back | Goes to home screen, app stays hidden |
| 9 | Biometric | Enable biometrics, open Chrome | Prompt appears automatically; success opens; *Use PIN or pattern* falls back to PIN |
| 10 | In-app gate | Leave App Lock, reopen it | Asks for PIN before showing settings |
| 11 | Usage-access mode | Disable the accessibility service | "App Lock is active" notification appears; locking still works (≈ 350 ms) |
| 12 | Reboot | `adb reboot` | Protection resumes after boot |
| 13 | Doze | `adb shell dumpsys deviceidle force-idle`, wake, open Chrome | Still locked |
| 14 | Self-protection | Enable *Lock Settings & uninstaller*, open Settings | PIN required |
| 15 | Uninstall protection | Enable *Prevent uninstall*, try uninstalling | System refuses until the admin is deactivated |
| 16 | Process death | `adb shell am kill com.adityadixit.applock` while Chrome is unlocked, reopen Chrome | Locked (sessions are in-memory only: fail closed) |
| 17 | Master switch | Settings › *App lock enabled* off | Nothing locks; list preserved |

### Logs

```bash
adb logcat -s LockCoordinator LockOverlay LockServiceLauncher CredentialRepository
```

### OEM notes

Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei and some Samsung builds aggressively kill background
apps. Besides the battery exemption, users may need to enable **Autostart** and **lock the app
in Recents**. See [dontkillmyapp.com](https://dontkillmyapp.com).

---

## Distribution note

Google Play does not allow the Accessibility API for app locking. Distribute via **GitHub
Releases / F-Droid**, or ship a Play flavour that relies only on Usage Access (see
ARCHITECTURE.md §10).

## License

[MIT](LICENSE)
