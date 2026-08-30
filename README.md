<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/banner_dark_medium_trimmed.png">
    <img src="assets/banner_light_medium_trimmed.png" alt="Biometric App Lock">
  </picture>
</h1>

<p align="center">
  Xposed module that locks apps you choose behind fingerprint or face unlock. It intercepts launches at the System Framework level, so a locked app's activities are never created until you authenticate.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 13+">
  <img src="https://img.shields.io/badge/libxposed-API_101%2B-ff69b4?style=for-the-badge" alt="libxposed API 101+">
  <img src="https://img.shields.io/github/downloads/hxreborn/biometric-app-lock/total?style=for-the-badge&logo=github&label=Downloads&cacheSeconds=600" alt="Downloads">
</p>

> [!NOTE]
> Built for AOSP and Pixel. Now also supports **Samsung (One UI)** and **Xiaomi (HyperOS / MIUI)** devices with native OEM face unlock. Other lightly modified flavors may work but are untested.

## About this module

Stock Android has no native per-app lock. This module adds one. A locked app opens normally once you authenticate, including when you tap it from the recents screen.

Enabling the module needs one reboot so it loads at boot. After that, if your framework supports hot reload, app updates apply with no reboot. If not, you still reboot after each update. Changing which apps are locked is always instant.

## OEM face unlock support

The standard Android `BiometricPrompt` hides OEM face unlock from third-party apps when only face biometrics are enrolled (no fingerprint). This module works around that limitation:

- **Samsung (One UI)**: Falls back to device credential authentication, which triggers Samsung's native face scanner automatically.
- **Xiaomi (HyperOS / MIUI)**: Bypasses the restriction entirely by communicating directly with `miui.face.FaceService` via raw Binder IPC. The native front-camera face scanner activates silently in the background while the system prompt is displayed, and unlocks the app instantly on match.

No additional setup is needed — the module detects the device manufacturer and applies the correct strategy automatically.

## Requirements

- Android 13+ with an enrolled biometric
- Xposed manager with libxposed API 101 or newer (102 enables hot reload)

## Install

1. Install APK from [Releases](../../releases)
2. Enable module in your Xposed manager with System Framework scope
3. Reboot
4. Select apps to lock in the Apps tab

## Prevent uninstall

Toggle in Settings → Privacy & stealth. While on, the module blocks every attempt to uninstall itself, including `adb uninstall` and `pm uninstall`, since it's enforced in the system framework.

> [!IMPORTANT]
> To remove the module with the toggle on:
> 1. Turn it off in the app, then uninstall. No reboot needed.
> 2. If for some reason you can't open the app to disable the toggle, either disable the module in your Xposed manager and reboot or boot to safe mode where Xposed is off and uninstall.

## Block screenshots

Toggle it globally in Settings or per app in its detail screen. Changes apply immediately, no reboot or hot reload. While on, an unlocked locked app's screenshots, screen recording, and recents preview are blocked. The per-app setting overrides the global one, so an app can stay blocked with the global toggle off. It cannot beat modules that force FLAG_SECURE off, like Disable Flag Secure by aviraxp.

After you turn the block off, that app's recents card can stay blank since the system cached it while the block was on. Swipe it off recents and reopen the app for a fresh preview.

## Reporting issues

Settings → About → Export logs saves the module's log lines to a text file and opens a share sheet, so you can attach them to a [GitHub issue](https://github.com/hxreborn/biometric-app-lock/issues/new), send them by [email](mailto:hxreborn@duck.com), or share via [Telegram](https://t.me/hxreb0rn). Reproduce the issue first, then export.

Reading the LSPosed logs needs root. Only this module's own log lines are exported, so they're safe to attach to a public issue. Debug builds help me most.

## License

[![GPL-3.0-only](https://img.shields.io/badge/LICENSE-GPL--3.0--only-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](LICENSE)
