# Release

## Local Build

```bash
./gradlew lint testDebugUnitTest assembleDebug
```

The installable debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Release

Push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

`.github/workflows/release.yml` builds, tests, packages the debug APK, writes a SHA-256 checksum,
and uploads both files to a release.

## Signing

The default release workflow intentionally publishes a debug-signed APK because this repository does
not include private signing keys. Debug-signed APKs are installable from GitHub Releases, but they
are not suitable for Play Store distribution or long-term production update chains.

For production signing:

1. Create a private Android keystore outside the repository.
2. Store the keystore and passwords as GitHub Actions secrets.
3. Add a release signing config in `app/build.gradle.kts`.
4. Build `assembleRelease` and upload the signed release APK instead of the debug APK.

Never commit keystores, passwords, refresh tokens, API keys, or OAuth secrets.
