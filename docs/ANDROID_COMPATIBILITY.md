# Android Compatibility

CodexBar Android uses Android-native background primitives instead of copying the macOS menu bar
runtime model. The refresh path is built around WorkManager, boot/app-update recovery, notification
permission handling, home-screen widgets, Quick Settings tiles, encrypted local credential storage,
and OEM settings shortcuts.

## ColorOS 16 / OPPO / OnePlus / realme

ColorOS-style systems can delay or stop background jobs unless the user allows the app through OEM
controls. CodexBar detects OPPO, OPlus, OnePlus, and realme builds and exposes shortcuts in
Settings for:

- notification settings
- battery optimization / unrestricted battery page
- autostart or background launch page
- app details fallback

Recommended setup:

1. Allow notifications.
2. Set battery usage to unrestricted.
3. Allow autostart or background launch.
4. Keep the persistent quota notification enabled.

## Other Strict OEMs

The same compatibility layer covers common background-management pages for Xiaomi / Redmi / POCO /
HyperOS / MIUI, vivo / iQOO / OriginOS, Huawei / HarmonyOS, Honor / MagicOS, Samsung / One UI, and
stock Android.

## Refresh Behavior

WorkManager has a platform minimum of 15 minutes for periodic jobs. CodexBar respects that floor.
When the user chooses Manual, periodic quota and token jobs are cancelled; pull-to-refresh and the
notification refresh action still enqueue a one-time refresh.

Transient token-refresh failures return `RETRY`, so WorkManager applies backoff. Terminal OAuth
failures such as `invalid_grant` stop retrying until the user replaces credentials.

## Troubleshooting

If data becomes stale on a strict OEM ROM:

1. Open Settings -> Android Reliability.
2. Open each listed system page and allow CodexBar.
3. Pull to refresh once from the dashboard.
4. Reboot the phone and confirm the persistent notification returns after unlock.

