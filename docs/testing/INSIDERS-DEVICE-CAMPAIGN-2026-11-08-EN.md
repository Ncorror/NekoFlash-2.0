> **PREPARED PLAN, NOT AN EXECUTED FIELD REPORT.** PR #62's TestBook cards are potential tasks. H3 1023/1024/1025, recursive 512-file transfers, 16 MiB and historic H1/H2 real recovery/vendor_boot flashing were **already executed on recorded builds**. The **terminal output/header alignment after rotation also has a real Nov 7 physical PASS**, with tester screenshot recorded in [PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45#issuecomment-6707179253); the operator reports three submissions. Do not reassign the same rotation check. Never schedule an automatic repeat without a specific changed SHA/device/missing evidence. Consult the [physical evidence ledger](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) first; agree the next small delta in chat. Do not overwrite old QA data; no destructive LAB on locked hardware.

# NekoFlash 2.0 — Insiders Physical Device Campaign, November 8, 2026

**Purpose:** validate the post-REV54/55 development build on real Android devices, including logs and authorized destructive laboratory operations. Retain raw evidence. [Русская инструкция](INSIDERS-DEVICE-CAMPAIGN-2026-11-08-RU.md).

## 0. Exact build and TestBook

- Download `NekoFlash-Insiders-Tester-Kit` from one **successful main branch GitHub Actions run** after the campaign is integrated. Extract **exactly two files**: Debug APK and `testbook.html`. The packaged book embeds the exact Git SHA, CI run and APK SHA-256. The published REV45 reference kit is **not** the current test APK.
- Before updating DEV, export previous diagnostics and actual saved JSON progress. Do not uninstall the original NekoFlash.
- TestBook layout, storage, RU/EN switch, report handling and six quick tasks are unchanged. Advanced mode contains 43 cases; LAB-01…LAB-06 are explicit destructive-lab cases. Unperformed cases must be BLOCKED/SKIP, never PASS.
- PASS means executed and verified, FAIL means observed defect, BLOCKED means missing prerequisites, SKIP means intentionally not run.

## 1. Device roles

- **HOST:** POCO F7 (onyx), Android 16, NekoFlash DEV over a known-good data-capable OTG connection.
- **SAFE TARGET:** second POCO X3 Pro (vayu), Android 12, **locked bootloader**. Use for read-only ADB/Fastboot, UI, USB reconnect and disposable files under `/sdcard/Download`. **Never flash/format/unlock this target as part of the test.**
- **LAB TARGET:** separate already-unlocked disposable vayu Android 14 or another explicitly approved spare device. First verify real unlock state, codename, partition topology, image compatibility, offline backups and recovery route. Do not infer slots or available `recovery`/`vendor_boot` partitions from model names.

Disconnect USB intentionally **only during disposable ADB push**, not during Fastboot DATA, sideload or partition writes.

## 2. Non-destructive stages

**A — Setup, UI and terminal:** `SETUP-01`, `UI-01/02`, `TERM-01/02/03/04/05/06`, `USB-01`, `ADB-01/02/04/06`, `CI-01/02`. Verify exact installed APK, Gboard/IME, Cyrillic and emoji, 130% font scale, screen rotation during input and after command submission, output position, sheet header, Close versus Stop and absence of stale 100% overlays or false SUCCESS. Keep full trace when a bug occurs.

**B — USB/ADB transport:** `ADB-03`: send source files of **1015/1016/1017 bytes**, corresponding to 1023/1024/1025-byte ADB SYNC DATA WRTE frames with the 8-byte header. Verify target SHA-256 and no delayed/misattributed A_OKAY. Push/pull 16 MiB and compare byte-for-byte on HOST. `ADB-08`: recursive push/pull of a **512-file tree (511 data files plus sha256sums.txt)**, verify names, byte lengths and hashes. `ADB-09`: Stop during disposable push. `USB-03`: detach during disposable ADB push only, then reconnect and verify normal shell. Preserve all partial files and logs.

**C — Fastboot and UI without writes:** `FB-01/02/04` are read-only getvar and mode checks. `QUICK-01/MASS-01` inspect image selection, slot and queue without execution. `SIDE-01` cancels ZIP picker **without selecting a file** (selection may immediately start transport). `UNLOCK-01` is UI only. `FILE-01`, `SAF-01/02` use disposable file imports.

**D — Evidence and Chrome TestBook:** Run a 10–30 minute harmless session with repeated messages/reconnects, then `LOG-01/03`: export raw diagnostics via NekoFlash APK, inspect `EXPORT-MANIFEST.txt`, check every ZIP SHA-256/size and compare available source folder inventory using read-only `verify_diagnostic_zip.py --check-source-inventory`. Do not bypass Android sandbox for private directories. `LOG-02`: in Android Chrome `content://`, test Copy and selectable fallback, physically saved JSON/ZIP, actual JSON re-import; an anchor tap is NOT successful file saving. If browser export fails, retain selectable text and separately export the raw APK ZIP. `REPORT-01`: preserve exact time, build ID, USB cable, HOST/TARGET identity, screenshots, actual statuses.

## 3. Prepare disposable corpus on HOST Termux

Commands below never overwrite an existing QA directory. Use a fresh unique suffix if the example location already exists.

```bash
termux-setup-storage
ROOT="$HOME/storage/downloads/NekoFlashQA-REV56"
if [ -e "$ROOT" ]; then echo "QA_STOP_EXISTS: choose a fresh suffix"; else
  mkdir -p "$ROOT/tree" "$ROOT/return"
  for n in 1015 1016 1017; do head -c "$n" /dev/urandom > "$ROOT/boundary-$n.bin"; done
  dd if=/dev/urandom of="$ROOT/probe16.bin" bs=1M count=16
  for i in $(seq 1 511); do
    dir=$(printf "set-%02d" $(((i-1)/32)))
    name=$(printf "file-%04d.bin" "$i")
    mkdir -p "$ROOT/tree/$dir"
    size=$((256 + (i % 13) * 173))
    [ "$i" -eq 224 ] && size=1016
    head -c "$size" /dev/urandom > "$ROOT/tree/$dir/$name"
  done
  (cd "$ROOT/tree" && find . -type f -name '*.bin' -print0 | sort -z | xargs -0 sha256sum > sha256sums.txt)
  echo "QA_READY $ROOT"
  find "$ROOT/tree" -type f | wc -l
fi
```

Expect **512 files** in `tree` including the checksum manifest. Execute `adb push/pull` in the NekoFlash terminal, and HOST `cmp`, `diff`, `sha256sum` in Termux. Use a dedicated unused TARGET `/sdcard/Download/NekoFlashQA-REV56-REMOTE` path. Verify every returned file, not just the count. No USERDATA, partitions or firmware images are touched by these preparation commands.

## 4. Authorized destructive lab only

All LAB cases require `LAB-01 PASS` first; no lab status can be inherited from a previous TestBook version.

1. **LAB-01 — preflight.** Confirm exact product, actual unlocked bootloader, A-only/A/B topology, expected partition and slot, matching firmware/base build, image/ZIP SHA-256, full off-target backups, functional Fastboot/Recovery, spare cable, battery and a documented rollback procedure. Any missing item = BLOCKED for **all** remaining LAB cases.
2. **LAB-02 — Quick Flash.** Write one explicitly approved, recoverable and model-matched partition image to a disposable unlocked TARGET. Never choose an unverified image or critical bootloader/modem/persist/EFS partition. Require real final Fastboot OKAY/FAIL, a successful reboot/function check and complete logs.
3. **LAB-03 — Fastboot/Fastbootd post-DATA.** Identify proper mode with `fastboot getvar is-userspace`. Flash only the approved partition in the correct mode and slot. Do not guess A/B state or switch active slots blindly. Confirm final response after DATA, boot and recovery path.
4. **LAB-04 — Mass Flash.** Prepare a reviewed partition→image→slot→SHA-256→backup→rollback table and flash only the authorized recoverable partitions. Require an independent verdict per queue entry and final device boot, not just a UI 100% indicator.
5. **LAB-05 — actual Recovery ADB Sideload.** Use a model-compatible verified ZIP with a known working Recovery. Check the Recovery-side installation verdict separately from transfer progress; then verify reboot and installed state. A mere 100% transfer is not PASS.
6. **LAB-06 — userdata wipe/format.** Expendable already-unlocked hardware **only**. The operation may permanently remove local files, encryption keys and accounts. Verify no valued data remains and that backups can be read; execute only the specifically approved userdata reset and verify first boot plus restore. Do not unlock a locked device simply to complete a test.

Never erase or format bootloader, modem, persist, EFS, RPMB or other critical partitions in this campaign. Do not unplug USB in mid-flash. If an operation is incomplete, stop further writes and follow the verified device-specific recovery plan.

## 5. Report contract

Send complete TestBook ZIP (if actually saved by Chrome), separate NekoFlash raw diagnostic ZIP including `EXPORT-MANIFEST.txt`, screenshots/videos and a concise line `TEST ID | HOST | TARGET | build SHA | USB | PASS/FAIL/BLOCKED | expected/actual | time | rollback` through a trusted private channel. Never post private raw USB traces, serials, RSA material, accounts or partition backups to public GitHub issues.

CI green does not prove hardware behavior. One tested phone pair does not certify all devices. Keep [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49) and [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50) open until supported by real device evidence.
