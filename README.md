<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

NekoFlash lets one Android device act as a USB host for another Android device and provides ADB, Fastboot, Recovery/Sideload, diagnostics and Xiaomi account/unlock workflows without a PC.

**Latest signed release:** [v6.0.0-alpha12](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/v6.0.0-alpha12) · versionCode 235
**Release APK SHA-256:** `f5013cb895d11f7c42272075eb384e0b0bbc9d1fa3d537321504fbd01499e53b`

English · [Русский](README_ru.md)

**Project website:** [https://nekoflash.ncorror.chatgpt.site/](https://nekoflash.ncorror.chatgpt.site/)

## Use NekoFlash without a computer

Looking for a way to control or flash an Android device using **another Android phone instead of a PC**? Start with the workflow that matches your device mode:

- [Fastboot from Android without a PC](https://nekoflash.ncorror.chatgpt.site/guides/fastboot-from-android/) — detect a bootloader, inspect partitions and run device-appropriate Fastboot operations over USB OTG.
- [Flash a compatible recovery image (TWRP / OrangeFox) from Android](https://nekoflash.ncorror.chatgpt.site/guides/flash-recovery-twrp/) — understand device-specific recovery and boot partition layouts before writing.
- [ADB Sideload from an Android phone](https://nekoflash.ncorror.chatgpt.site/guides/adb-sideload/) — choose and send an update ZIP to a target in Recovery sideload mode.
- [ADB without a PC: shell, files and APK installation](https://nekoflash.ncorror.chatgpt.site/help/#modes) — connect a device with USB debugging, authorize ADB and use the built-in terminal, Push/Pull and APK installation.
- [Xiaomi / Redmi / POCO bootloader unlock workflow](https://nekoflash.ncorror.chatgpt.site/help/#unlock) — Mi Account sign-in and Fastboot detection are tested, but full bootloader unlocking **still requires a new physical-device validation** in NekoFlash 2.0. Xiaomi eligibility and waiting periods still apply.

**Official download:** [signed NekoFlash 2.0 APK from GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases/latest). The public source and verified releases are maintained by [Ncorror](https://github.com/Ncorror).


## Current state

Signed alpha12 is published with the completed audit, GitHub/Telegram Settings updates and the protected permanent-certificate release workflow. Hardware qualification remains the accepted alpha11 evidence; alpha12 does not claim another physical-device campaign.

Hardware-qualified highlights:

- Fastboot format/wipe on unlocked POCO X3 Pro `vayu`: `format userdata`, `fastboot -w`, metadata/cache handling, reboot and Android setup boot — **FIELD PASS**.
- ADB Pull/Push control on POCO X3 Pro host → POCO X3 Pro target: `framework.jar` Pull and repeated 128 MiB Pull/Push with matching SHA-256 — **FIELD PASS**.
- POCO F7 / `onyx` remains a documented intermittent USB-host receive compatibility case. Large Pull uses durable checkpoints and final SHA-256 verification; failed USB packets are never silently replayed.
- Recovery/Sideload, Mass Flash, Fastboot transitions, Xiaomi sign-in, ZIP select/clear and terminal rotation/input have accepted physical evidence on the tested rigs.

See [current status](docs/STATUS.md) and [hardware validation](docs/HARDWARE_VALIDATION.md).

## Downloads

Use [GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases/latest) for the signed public APK.

A release intentionally publishes only:
- the APK;
- its SHA-256 file;
- `filesystem-tools-source.tar.gz`, required corresponding source for bundled filesystem tools.

Debug/test artifacts from Actions are not production releases.

## Main features

- ADB shell, install, push/pull and diagnostics.
- Quick Flash, Mass Flash and direct Fastboot commands.
- Recovery ADB Sideload with non-mutating ZIP selection and an explicit Start action.
- Xiaomi account/session and unlock workflow.
- Operation Center with full diagnostic evidence export.
- Host-side ext4/F2FS generation for supported Fastboot formatting.
- Large-file Pull checkpoint/resume with final source/destination SHA-256 verification.

## Safety model

NekoFlash is a professional tool. It keeps full raw diagnostic evidence and does not hide direct ADB/Fastboot functionality. It also does not silently retry an ambiguous destructive Fastboot write or a failed ADB packet.

Raw diagnostics may contain device identifiers, commands and paths. Sanitized/shareable exports are a separate copy and do not modify the raw evidence. See [Privacy](PRIVACY.md), [Security](docs/SECURITY.md) and [Professional recovery policy](docs/PROFESSIONAL_RECOVERY_POLICY.md).

## Documentation

Start at [docs/README.md](docs/README.md).

Key documents:
- [Status](docs/STATUS.md)
- [Post-release audit](docs/AUDIT_2026-11-13.md)
- [Hardware validation](docs/HARDWARE_VALIDATION.md)
- [USB troubleshooting](docs/USB_CONNECTION_TROUBLESHOOTING.md)
- [Repository/release security](docs/REPOSITORY_SECURITY.md)
- [Legal review](docs/LEGAL_REVIEW.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

Historical REV/audit snapshots are preserved by Git history rather than kept as competing current instructions.

## Project

Author and maintainer — [Ncorror](https://github.com/Ncorror). Project collaborator — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork terms](ASSETS_LICENSE.md) · [Changelog](CHANGELOG.md)
