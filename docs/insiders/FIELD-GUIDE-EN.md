> **HISTORICAL REV45/46 FIELD GUIDE — NOT A NEW TEST ASSIGNMENT.** Real USB 1023/1024/1025, recursive 512-file push/pull, 16 MiB and older real Recovery/Fastbootd/Sideload cases have already been exercised on explicitly recorded devices/builds. Read the [hardware evidence ledger](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) and [current Insiders landing page](README.md) before planning any new work. Never rerun the QA commands below over an existing corpus. Destructive LAB is excluded on a locked TARGET and requires a separate approved restore plan. Preserve historic TestBook progress; agree next tests from the unverified delta in chat.

# NekoFlash 2.0 field guide · Insiders REV45

Extract the ZIP, install the APK on the host and open testbook.html in a browser. The build panel lists the commit, CI run and APK digest. Use JSON export to save progress.

[Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy)

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
