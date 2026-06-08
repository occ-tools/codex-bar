# CodexBar Android

Android-native quota monitor for AI coding tools, based on the ideas and provider model from
[CodexBar](https://github.com/steipete/CodexBar) and the MIT Android port by
[hyunnnchoi](https://github.com/hyunnnchoi/CodexBar-android).

This is not a macOS UI clone. The Android version keeps the quota-provider logic, then adapts the
runtime model for phones: WorkManager scheduling, boot recovery, notification permission handling,
home-screen widgets, Quick Settings tiles, encrypted local credential storage, and OEM background
compatibility for ColorOS/OPlus, realme UI, HyperOS/MIUI, OriginOS, HarmonyOS/MagicOS, Samsung, and
stock Android.

## Features

- Quota monitoring for Claude, Codex / ChatGPT, and Gemini.
- Dashboard cards with per-window usage, reset time, tier, and error state.
- Pull-to-refresh plus a deduplicated manual background refresh job.
- Background quota and token refresh with WorkManager backoff.
- Persistent notification with manual refresh action.
- Quota reset alerts.
- Quick Settings tile.
- Home-screen widget.
- EncryptedSharedPreferences credential storage.
- Android 13+ notification permission guard.
- Battery optimization, autostart, and app-detail shortcuts for strict OEM ROMs such as ColorOS 16.
- App backup disabled and cleartext traffic disabled.

## Android Compatibility

Minimum Android version: Android 8.0 / API 26.

Recommended setup for OPPO / OnePlus / realme / ColorOS 16:

1. Enable CodexBar notifications.
2. Set CodexBar battery mode to unrestricted.
3. Allow background launch or autostart for CodexBar.
4. Keep the persistent notification enabled if you expect reliable background refresh.

The Settings screen detects the device family and opens the most specific available system page.
If an OEM hides a private settings activity, CodexBar falls back to the normal Android app details
page.

More detail: [docs/ANDROID_COMPATIBILITY.md](docs/ANDROID_COMPATIBILITY.md).

## Privacy

- No backend server.
- No password storage.
- Tokens stay on device in Android encrypted storage.
- Android backup is disabled so stored credentials are not copied into cloud/device backup.
- Debug network logging is limited to request basics and does not log headers or bodies.

You are still responsible for protecting refresh tokens. Prefer a secondary account or a token that
you can revoke while testing APKs from GitHub Releases.

## Credentials

### Claude

Claude uses OAuth credentials from Claude Code. Store both access token and refresh token in
Settings. The app treats Claude access tokens as short-lived and proactively refreshes them before
expiry. OAuth `invalid_grant` still requires re-authentication.

### Codex / ChatGPT

Use the access token and refresh token from the Codex CLI auth file, usually `~/.codex/auth.json`.
The optional account ID is passed as `ChatGPT-Account-Id` when provided.

### Gemini

Gemini needs:

- access token
- refresh token
- OAuth client ID
- OAuth client secret

These values can be extracted from Gemini CLI credentials and source constants.

## Build

Requirements:

- JDK 17
- Android SDK with API 35+
- Android Studio or Gradle Wrapper

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Tag pushes create a release APK through GitHub Actions:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow uploads a debug-signed APK. It is installable, but it is not a production signing key.
For Play Store or long-term update stability, add a release signing configuration and repository
secrets before publishing production APKs.

Release notes and signing guidance: [docs/RELEASE.md](docs/RELEASE.md).

## Project Structure

```text
app/src/main/java/com/codexbar/android/core/data          provider repositories
app/src/main/java/com/codexbar/android/core/network       Retrofit API clients
app/src/main/java/com/codexbar/android/core/oem           OEM compatibility intents
app/src/main/java/com/codexbar/android/core/workmanager   background refresh scheduling
app/src/main/java/com/codexbar/android/feature/dashboard  quota dashboard UI
app/src/main/java/com/codexbar/android/feature/settings   credentials and Android reliability UI
```

## Attribution

This project keeps the MIT license and attribution from:

- [steipete/CodexBar](https://github.com/steipete/CodexBar)
- [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android)

## License

[MIT](LICENSE)
