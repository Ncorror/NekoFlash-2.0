<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Latest accepted main baseline:** `7273b014`, `6.0.0-alpha11`; [GitHub Actions #698 SUCCESS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43068208355) (JVM, Lint, Android API 26 and 34). The next Insiders build distributes **only the debug APK**, with source provenance checked in CI. Hardware test instructions and results are now handled **in the team chat**, not a browser checklist. Raw diagnostic ZIPs are exported from NekoFlash itself.

**Previous published Insiders REV50** ([release history](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/insiders-rev50)) is an archived debug prerelease from an older source commit, not the latest main or signed production APK. Existing published assets remain historical.

**New improvements:** Clear chosen Sideload ZIP without deleting it, show Xiaomi account authorization progress, and display Android version from the connected ADB SYSTEM device. Outstanding [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72) inbound Pull EPROTO and [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64) Fastboot wipe/cache reply remain separate hardware investigations; experimental [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83) is not included.

English · [Русский](README_ru.md)

NekoFlash operates ADB and Fastboot between Android phones via USB OTG. Host Android 8+, USB Host and a data cable required. Root on the host is optional.

**Testing downloads:** [successful main CI artifacts](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) · [Telegram Insiders](https://t.me/+NR_FiUmvXlViOTUy) · [historical hardware evidence](docs/testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). Download the APK for the exact tested commit, record build ID and send screenshots + full diagnostic export privately. Previously completed flashing and terminal rotation are not assigned again.

## Inside the app

The console supports ADB shell, push/pull and APK installation. The Flash tab contains Quick Flash, a Mass Flash queue and Fastboot tools. The app also has recovery Sideload, Xiaomi Unlock, operation reports and raw USB diagnostics.

English interface of the published REV45 APK on an Android 14 emulator (API 34). Home shows a disconnected host. The Mass Flash queue contains a test image.

| Home | Terminal | Quick Flash |
| --- | --- | --- |
| <img src="docs/screenshots/rev45/portrait-home.png" width="210" alt="Home"> | <img src="docs/screenshots/rev45/terminal-command.png" width="210" alt="Terminal"> | <img src="docs/screenshots/rev45/portrait-quick.png" width="210" alt="Quick Flash"> |

| Mass Flash queue | Fastboot tools | ADB Sideload |
| --- | --- | --- |
| <img src="docs/screenshots/rev45/mass-queue.png" width="210" alt="Mass Flash queue"> | <img src="docs/screenshots/rev45/portrait-tools.png" width="210" alt="Fastboot tools"> | <img src="docs/screenshots/rev45/portrait-sideload.png" width="210" alt="ADB Sideload"> |

## Build and evidence

Reference APK and gallery — `6.0.0-alpha11-dev` (234), `ru.forum.adbfastboottool.dev`, [`a31dedadd4ad`](https://github.com/Ncorror/NekoFlash-2.0/commit/a31dedadd4adcf08691ece82c7574f05286078f3). [CI #413](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886) · [APK ZIP](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886/artifacts/13139380119).

308 JVM tests passed. Android API 26 and 34 passed 20 instrumentation tests each. Lint reported zero findings. The APK SHA-256 and source commit are tracked by the exact CI run.

[Audit / Аудит](docs/insiders/AUDIT-2026-11-02.md) · [Hardware](docs/HARDWARE_VALIDATION.md) · [USB OTG](docs/USB_CONNECTION_TROUBLESHOOTING.md) · [Privacy](PRIVACY.md)

For a local debug build, use JDK 17 and the repository’s filesystem tool build steps. See the [build workflow](.github/workflows/build.yml).

## Project

Author and maintainer — [Ncorror](https://github.com/Ncorror). Project collaborator — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Previous README and release history](https://github.com/Ncorror/NekoFlash-2.0/blob/7b54ba1e70807bdc7cee65a64cff87fd002ce26b/README.md)

Topics — `android` `usb-otg` `usb-host` `adb` `fastboot` `adb-sideload` `android-recovery` `hardware-testing`
