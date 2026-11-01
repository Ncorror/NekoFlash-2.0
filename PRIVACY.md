<div align="center">

<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash Privacy Note

### Data privacy, telemetry, local operations, and offline integrity

English

</div>


This document describes the behavior of the current NekoFlash source tree. It is not a substitute for a jurisdiction-specific privacy policy if the application is later distributed through a store or service that requires one.

## Local device operations

ADB, Fastboot, USB discovery, file transfer, flashing, sideload, diagnostics and command execution are performed locally between the Android host running NekoFlash and the USB-connected target device.

The current source tree contains no analytics, advertising, third-party crash-reporting or telemetry SDK integration.

## Xiaomi / Mi Unlock network traffic

The optional Mi Unlock workflow requires Internet/network access and communicates over HTTPS with official Xiaomi account and unlock endpoints used by that flow, including `account.xiaomi.com`, `api.account.xiaomi.com` and the bounded regional unlock hosts defined in source.

Mi account identifiers, WebView cookies, `passToken`, `deviceId`, `ssecurity`, service cookies and unlock request data are handled only for the Mi Unlock workflow. **Authentication cookies may remain in Android WebView storage across Back/cancel and future launches until explicit Logout.** The current `MiLoginActivity` reuses valid existing cookies and calls `CookieManager.removeAllCookies` from its Logout action. Closing the screen clears in-memory fields and WebView state, but should **not** be treated as equivalent to logging out of the Xiaomi account. Use the explicit Logout control when the device is shared or when the session must be cleared. This persistence behavior is under review before release.

NekoFlash does not operate its own account, analytics or telemetry backend.

## Files and logs

NekoFlash uses a workspace in shared storage for selected payloads, imported files, logs, protocol traces and exported diagnostic reports. In the current source, full diagnostic logs and reports may be stored under `/sdcard/Download/NekoFlash/` and include commands, device properties, filenames, USB identifiers and operation results. **Shared-storage files are not app-private**: other apps with sufficient storage privileges can read them. Do not treat raw logs or screenshots as safe to publish.

Sanitized sharing/export paths apply the project's report sanitization logic, but **redaction is not a complete guarantee**: for example, a serial supplied as `fastboot -s SERIAL getvar all` is not covered by the current `serial:` / `serial=` pattern. The "Copy terminal" action copies a raw transcript to Android's clipboard, which may contain identifiers and sensitive paths. **Review and manually redact exported material before sending it to anyone**; prefer short, purpose-specific test logs rather than full sessions.

The ADB host private key is currently persisted as an app-private PKCS#8 file (`adbkey.pk8`), not a non-exportable Android Keystore key. The application declares broad storage access because the current distribution model expects direct access to firmware and diagnostic files. Android application backup is disabled. Changes to raw-log storage and export sanitization are tracked in [the security backlog](docs/SECURITY_REVIEW_BACKLOG.md).

## Permissions relevant to data handling

The manifest declares Internet/network state permissions for the optional Xiaomi / Mi Unlock feature, storage access for user-selected firmware/diagnostic files, foreground-service and wake-lock permissions for long USB operations, notification permission where required by Android, and overlay-hiding protection for sensitive screens. It does not request contacts, SMS, microphone, camera, phone or location permissions.
