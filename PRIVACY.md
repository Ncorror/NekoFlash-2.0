# NekoFlash Privacy Note

This document describes the current NekoFlash source tree. It is not a substitute for a jurisdiction-specific store privacy policy.

## Local operations

ADB, Fastboot, USB discovery, file transfer, flashing, Sideload, diagnostics and command execution are performed locally between the Android host running NekoFlash and the USB-connected target.

The current source tree contains no analytics, advertising or third-party crash-reporting SDK.

## Xiaomi / Mi Unlock

The optional Xiaomi workflow requires network access to official Xiaomi account/unlock endpoints used by the implementation.

WebView authentication cookies and session values can persist across Back/cancel and future launches. Valid existing cookies may be reused. **Back/cancel is not Logout.** The explicit Logout action clears WebView cookies.

Use Logout on a shared host phone or whenever the Xiaomi session must be removed.

NekoFlash does not operate its own account/telemetry backend.

## Files and raw diagnostics

NekoFlash uses shared storage/workspace paths for firmware, imported files, logs and diagnostic exports. Raw evidence under paths such as `/sdcard/Download/NekoFlash/` can contain commands, device properties, filenames, USB identifiers and operation results.

Shared storage is not app-private. Other applications with sufficient storage access may read those files. Do not publish raw logs/screenshots without review.

The project intentionally preserves full raw technical evidence for professional recovery. Raw logs are not silently shortened or sanitized.

## Sanitized/shareable copies

Sanitized export is a separate copy. Current `main` masks structured device/account identifiers, sensitive device properties, known private/shared paths and explicit `adb -s SERIAL` / `fastboot -s SERIAL` selectors.

Sanitization is defense-in-depth, not a promise that arbitrary command output can never contain a sensitive value. Review material before public sharing.

The signed alpha11 APK predates the post-release selector-specific sanitizer hardening; alpha11 users should manually review even sanitized output.

## ADB identity

The ADB host private key is stored in app-private storage as a PKCS#8 identity. New private-key publication uses same-directory staging, owner-only permissions, fsync and atomic rename. Existing corrupt identity is preserved instead of silently rotated.

## Permissions

The application requests the Android permissions needed for USB/long-running operations, notifications, network access for optional Xiaomi login and broad storage access for the professional firmware/diagnostic workflow. It does not request contacts, SMS, microphone, camera, phone or location permissions.

See [docs/SECURITY.md](docs/SECURITY.md) and [docs/PROFESSIONAL_RECOVERY_POLICY.md](docs/PROFESSIONAL_RECOVERY_POLICY.md).
