<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Current development source: REV55** — `main` at [`efdc4a96e`](https://github.com/Ncorror/NekoFlash-2.0/commit/efdc4a96e479d360a57a8b7dbba49d2ff905af13) (8 Nov 2026). REV49/51/52/53/54/55 are merged; [PR #59](https://github.com/Ncorror/NekoFlash-2.0/pull/59) fixes the Android 8 **test fixture only**. Candidate CI [#501](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42426015449) passed 336 JVM tests, lint, 28/28 installed tests on each API 26 and 34, plus tester-kit packaging; API26 also passed an independent same-SHA rerun. The **post-merge `main` CI #502** must be checked [here](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327) before considering the integrated build fully green.

**Physical OTG acceptance (specific device pair):** REV54's candidate APK passed 1023/1024/1025-byte USB WRTE boundaries, recursive 512-file push/pull with SHA-256 checks and a 16 MiB round trip on POCO F7 host → locked POCO X3 Pro / Android 12 target; [evidence discussion](https://github.com/Ncorror/NekoFlash-2.0/issues/57). This does **not** certify other devices or destructive flashing. The experimental terminal PR #56 was closed unmerged and its branch deleted; the regular terminal remains. [Development status](docs/DEVELOPMENT_STATUS.md).

The downloadable REV45 kit and screenshots below remain **historical reference material**, not the newest REV55 APK. For an up-to-date test build use a **successful `main` workflow artifact**, check its recorded commit and SHA-256, and retain older diagnostics before replacing any DEV installation.

English · [Русский](README_ru.md)

Run ADB and Fastboot from one Android phone to service a second phone over USB OTG. The host needs Android 8.0+ and USB Host support. Use a data-capable cable. Root is optional on the host.

**Published reference kit · REV45 (historical)**

The release-linked download below is the older REV45 development build, not the current REV55 source. The debug app installs alongside the original NekoFlash. Physical OTG, reconnect and partition-write results are still pending for this build.

**[Download the Insiders tester kit (ZIP)](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip)** · [Official GitHub Actions downloads](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) · [NekoFlash Insiders · Telegram](https://t.me/+NR_FiUmvXlViOTUy)

Open the ZIP on the **host** phone. Install the included debug APK, then open `testbook.html` in a browser. The ZIP contains two files. The offline testbook includes RU/EN instructions, the APK checksum and the Telegram group link. For a newer CI snapshot, sign in to GitHub, open a successful main run and choose **NekoFlash-Insiders-Tester-Kit** under Artifacts.

[Field guide](docs/insiders/FIELD-GUIDE-EN.md) · [Offline testbook RU/EN](docs/insiders/testbook.html)

For one test round, distribute the same downloaded ZIP to everyone. The previous REV43 and reference REV45 APKs have different debug signing certificates. Back up DEV logs and export testbook progress before replacing a conflicting DEV installation.

## First test

Start with the interface and keyboard. Then connect a second phone, authorize ADB on the target and try `adb devices` and `adb shell getprop ro.product.model`. Record both models, Android versions, build ID and the observed result. Share the report ZIP privately with the test team.

> Flashing, erasing, formatting or unlocking can wipe the target or leave it unable to boot. Test writes only in an agreed lab session with a spare target, a backup and a verified recovery path.

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

308 JVM tests passed. Android API 26 and 34 passed 20 instrumentation tests each. Lint reported zero findings. Each new tester kit records its own commit and APK hash in `testbook.html`.

[Audit / Аудит](docs/insiders/AUDIT-2026-11-02.md) · [Hardware](docs/HARDWARE_VALIDATION.md) · [USB OTG](docs/USB_CONNECTION_TROUBLESHOOTING.md) · [Privacy](PRIVACY.md)

For a local debug build, use JDK 17 and the repository’s filesystem tool build steps. See the [build workflow](.github/workflows/build.yml).

## Project

Author and maintainer — [Ncorror](https://github.com/Ncorror). Project collaborator — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Previous README and release history](https://github.com/Ncorror/NekoFlash-2.0/blob/7b54ba1e70807bdc7cee65a64cff87fd002ce26b/README.md)

Topics — `android` `usb-otg` `usb-host` `adb` `fastboot` `adb-sideload` `android-recovery` `hardware-testing`
