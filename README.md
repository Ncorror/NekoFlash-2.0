<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Release line:** `6.0.0-alpha11` · versionCode `234`.

NekoFlash operates ADB, Fastboot and Recovery workflows directly from one Android phone to another over USB OTG. Host requirements: Android 8.0+, USB Host support and a data-capable cable. Root on the host is optional.

English · [Русский](README_ru.md)

## Release status

The final alpha11 hardware campaign is closed for release:

- **Fastboot format/wipe (#64): FIELD PASS.** `fastboot format userdata`, metadata/cache handling, `fastboot -w`, reboot and subsequent Android setup boot were verified on the unlocked POCO X3 Pro `vayu`.
- **Large ADB Pull (#72): resolved.** POCO X3 Pro host → POCO X3 Pro target passed `framework.jar` Pull plus repeated 128 MiB Pull/Push with matching SHA-256. POCO F7 / `onyx` is classified as an intermittent host-side USB receive compatibility case; large Pull now uses durable 256 KiB checkpoints and verifies the final SHA-256 before publication.
- Recovery/Sideload, Fastboot handshake/reboot, Mass Flash, Xiaomi account sign-in, ZIP select/clear and terminal rotation/input have physical PASS evidence on the tested devices/builds.

The authoritative current summary is [Hardware validation](docs/HARDWARE_VALIDATION.md). Dated low-level experiments remain only in the [physical evidence ledger](docs/testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md).

## POCO F7 / onyx host note

POCO F7 (`onyx`, Android 16) has repeatedly shown intermittent inbound USB failures as a host, including in Legacy NekoFlash. The same target/files pass on a POCO X3 Pro host. NekoFlash therefore keeps fail-closed transport behavior and resumes large Pulls only from fully fsynced checkpoints after a manual reconnect.

Power/VBUS sensitivity is plausible field context but was not instrumented, so it is **not** claimed as the proven root cause. A short cable or externally powered USB hub is a useful diagnostic when a host has weak/unstable OTG power.

## Downloads

- [GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases) — official signed releases.
- [Successful main CI](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) — debug/test artifacts for the exact commit.
- [Telegram Insiders](https://t.me/+NR_FiUmvXlViOTUy) — tester coordination.

Do not treat an old Insiders REV ZIP as the current production artifact.

## What is included

- ADB shell, push/pull, APK install and diagnostics.
- Quick Flash, Mass Flash and Fastboot tools.
- Recovery ADB Sideload with separate file selection and explicit Start.
- Xiaomi Unlock account/session workflow.
- Operation Center, full diagnostic export and raw USB evidence.
- Host-side ext4/F2FS filesystem generation for supported Fastboot format commands.
- Checkpointed large-file Pull with source/destination SHA-256 verification.

## Safety and diagnostics

NekoFlash does not silently retry failed destructive Fastboot commands or failed ADB packets. Destructive operations remain explicit. Full diagnostic exports are kept for technical analysis; share raw logs only through a trusted channel because they can contain device/command context.

See [USB troubleshooting](docs/USB_CONNECTION_TROUBLESHOOTING.md), [Privacy](PRIVACY.md) and [Third-party notices](THIRD_PARTY_NOTICES.md).

## Build and release

CI uses JDK 17, the checked-in Gradle Wrapper and pinned GitHub Actions. Production packaging is fail-closed: the protected release workflow requires the permanent signing key, verifies certificate SHA-256 `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`, builds the release APK, verifies the APK certificate again and publishes checksums/build metadata.

The release tag must match `versionName` and be rooted at the approved `main` commit. See [release readiness](docs/RELEASE_READINESS-2026-11-10.md).

## Project

Author and maintainer — [Ncorror](https://github.com/Ncorror). Project collaborator — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Changelog](CHANGELOG.md)
