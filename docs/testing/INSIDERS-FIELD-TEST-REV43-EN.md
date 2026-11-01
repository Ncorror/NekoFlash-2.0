# NekoFlash 2.0 — Insiders REV43 field-testing guide (EN)

**Telegram test group: NekoFlash Insiders.** Test APK and offline testbook are intended for distribution there, with raw ZIP logs handled privately. No verified public invite link has been supplied; the direct GitHub downloads below are available.

**Status, 1 November 2026:** hardware HOST → TARGET testing is being organized. No new REV43 physical-device PASS is verified yet. This is a test manual, **not** a certificate that hardware tests passed.

## Downloads

- [Standalone offline RU/EN REV43 testbook](../insiders/testbook.html) — checklists, PASS / FAIL / BLOCKED / NOT TESTED, editable notes, saved JSON progress and original binary AutoLogs ZIP report.
- [Direct HTML download from main](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html). The GitHub Actions beta build also publishes a separately downloadable `NekoFlash-Insiders-Testbook-REV43-…` artifact.
- [REV43 debug APK artifact (ZIP)](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391/artifacts/13060555171): Android application ID `ru.forum.adbfastboottool.dev`, version `6.0.0-alpha11-dev`, versionCode 234. **Do not uninstall the original NekoFlash app.** Compute your own APK SHA-256 from the downloaded file; the REV29 checksum must not be reused.

**Source:** exact tested REV43 PR SHA `27ab796d100387b3530bd88a6faf28e48d1c717c`; merged DEV `bd0d1fbf0ff5ba69634df5281fa87e2ff1289bf5`.

## Build and CI reality

| Run | Verified result |
|---|---|
| [PR REV43 #41247325391](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391) | build/JVM/lint + Android API26 + API34 **PASS** |
| [Merged DEV #41248168147](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147) | Build **PASS**, API34 **PASS**, API26 **FAIL** |
| API26 failures | UI waits in `thousandRowsRecycleAndKeepOrderFormAndScrollAcrossRecreation` and `largeFontAndLandscapeKeepNavigationAndMassActionVisible`; missing UI capture evidence |
| Physical devices with REV43 | **Not yet verified**. Historical alpha10 hardware validations are separate. |

An emulator passing does not prove compatibility with a physical Android OTG host.

## HOST and TARGET

HOST is the Android phone running the NekoFlash DEV APK. TARGET is the connected second phone. Confirm USB data cable, Android host/device roles, USB permission, and RSA authorization for ADB. Use an existing unlocked **spare** target for Fastboot testing.

**Round 1:** without destructive actions, test app start, tabs, display/font/rotation, Gboard, Unicode, terminal select/copy and auto-follow, adb devices/getprop/id, USB roles and idle reconnect. Check [TERM-01](https://github.com/Ncorror/NekoFlash-2.0/issues/20).

**Round 2:** disposable-file ADB push/pull, SHA-256 equality, recursive `adb push -r` of 512 files, Stop during the transfer, USB detach/reconnect **during ADB push only**, inspection of any partial files, and HOST RAM / elapsed-time measurements. If no legacy target advertising small ADB `maxdata` is available, mark the dedicated case BLOCKED.

**Round 3:** real Fastboot DATA/flash/format/unlock/sideload is a **separate authorized lab phase** on a spare recoverable device with a documented restore route and backups. Do not unplug USB during a partition write. Read-only Fastboot getvar does **not** validate the post-DATA fix.

## Safe commands

In the NekoFlash terminal (with ADB-authorized TARGET):

```text
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell id
adb shell echo NEKOFLASH_TEST_OK
```

Prepare disposable data in HOST Termux, not on a production partition:

```sh
termux-setup-storage
mkdir -p ~/storage/downloads/NekoFlashQA/tree
dd if=/dev/urandom of=~/storage/downloads/NekoFlashQA/probe.bin bs=1M count=16
for i in $(seq 1 512); do
  d="part-$((i/32))"
  mkdir -p ~/storage/downloads/NekoFlashQA/tree/"$d"
  printf 'probe-%s\n' "$i" > ~/storage/downloads/NekoFlashQA/tree/"$d"/"file-$i.txt"
done
sha256sum ~/storage/downloads/NekoFlashQA/probe.bin
```

Then in NekoFlash terminal:

```text
adb shell mkdir -p /sdcard/Download/NekoFlashQA
adb push /sdcard/Download/NekoFlashQA/probe.bin /sdcard/Download/NekoFlashQA/probe.bin
adb pull /sdcard/Download/NekoFlashQA/probe.bin /sdcard/Download/NekoFlashQA/probe-return.bin
adb push /sdcard/Download/NekoFlashQA/tree /sdcard/Download/NekoFlashQA/
adb shell find /sdcard/Download/NekoFlashQA/tree -type f
```

Compare the source and returned file hashes in HOST Termux. Test Stop/disconnect only while sending disposable **ADB** data; never during Fastboot DATA or flashing.

## Test report

In the HTML enter models, OS versions, cable, exact APK build ID, each actual test outcome and steps to reproduce failures. Select `/Download/NekoFlash/logs` using its folder picker. The HTML cannot silently access files. On Android browsers offering only a snapshot, reselect the folder before exporting.

The offline ZIP contains `report.txt`, `progress.json`, `evidence-manifest.json`, raw `log-*`, `trace-*.bin`, `session-summary-*` where available, and manually selected screenshots/videos. It **does not modify, redact or delete** original evidence. ZIP32 is limited to about 4 GiB per archive; when exceeded, it errors without deleting source logs. Split large collections into multiple session exports.

Share the full ZIP **only in a trusted private test channel**. Never post raw serials, RSA material, Mi tokens, or private traces in public GitHub issues. The testbook has no automatic network submission.

**Open follow-up:** physical OTG/RAM measurements, USB detach/cancel, legacy maxdata, TERM-01 and intermittent Android API26 UI tests ([issue #16](https://github.com/Ncorror/NekoFlash-2.0/issues/16)). The `main` release branch remains untouched.
