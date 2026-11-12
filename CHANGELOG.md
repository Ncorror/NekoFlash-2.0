<div align="center">

<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" alt="NekoFlash Logo" />

# NekoFlash Changelog

</div>

## 6.0.0-alpha11 — 13 November 2026

Release candidate promoted to the protected signed-release path after the final hardware audit.

### Hardware and transport

- Closed #64 after real-device PASS of `fastboot format userdata`, `fastboot -w`, metadata/cache handling, reboot and Android setup boot on unlocked POCO X3 Pro `vayu`.
- Closed #72 after cross-host control: POCO X3 Pro host completed `framework.jar` Pull and repeated 128 MiB Pull/Push with exact SHA-256 equality.
- Classified POCO F7 / `onyx` as an intermittent host-side USB receive compatibility case with matching Legacy-era evidence.
- Added large-file Pull durability: 256 KiB fsynced checkpoints, manual reconnect resume, source STAT recheck and final SHA-256 verification before atomic publication. Failed USB/ADB packets are never replayed.
- Preserved fail-closed USB/Fastboot behavior and accurate transport failure provenance.

### Recovery, flashing and UI

- Sideload file selection no longer starts transfer; explicit Start remains required and selected ZIP can be cleared without deleting the source file.
- Recovery/Sideload result handling preserves verification state instead of converting near-complete disconnects into false success.
- Fastboot format/wipe handling supports vayu/no-slot behavior and waits for legitimate slow final bootloader replies.
- Terminal rotation/input/header regressions and Operation Center state handling were polished and hardware-tested.
- Xiaomi account authorization progress and connected Android version reporting are included.

### Diagnostics and release engineering

- Full diagnostic ZIP/export remains the primary evidence source.
- Removed obsolete TestBook/REV campaign documents, old automatic Insiders prerelease pinning and the legacy gallery workflow from the active release path.
- GitHub Actions used by build/release are pinned to exact commit SHAs.
- Protected release workflow validates main provenance, permanent certificate SHA-256, signed APK certificate, checksums and companion filesystem source before publication.

## 6.0.0-alpha10.2

Historical signed release. See GitHub Releases and repository history for the exact tagged source and assets.

## 6.0.0-alpha10.1

Historical hotfix release with the earlier POCO X3 Pro ADB/Fastboot/Recovery validation cycle.

## Earlier development history

Detailed REV/TestBook-era notes remain available in Git history and closed issues/PRs. They are intentionally not kept as active release instructions.
