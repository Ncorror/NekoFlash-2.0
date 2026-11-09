<div align="center">

## New physical field evidence — 9 November 2026, unlocked vayu

This section **supplements** (does not retroactively rewrite) the 8 Nov and alpha10/REV48 history below. [Full RU details](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md) · [EN summary](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md).

| Real hardware result | Classification |
| --- | --- |
| HOST POCO F7 → **unlocked A-only vayu**, current `main` build `6.0.0-alpha11+6a8accd8e33e.42517047929`: Mass Flash `boot` 128 MiB + `dtbo` 32 MiB + `recovery` 128 MiB; all final Fastboot OKAY, subsequent Recovery booted | **FIELD PASS 3/3**, not an Android-system boot guarantee |
| `fastboot format userdata` generated F2FS + flash OKAY; Android could not boot, `init_user0_failed` | **Android boot FAIL / reason UNKNOWN** — [#65](https://github.com/Ncorror/NekoFlash-2.0/issues/65) |
| `fastboot -w`: `userdata` erased and rewritten; `erase:cache` no final reply for ~57/85 seconds, cancelled; metadata not erased | **PARTIAL DESTRUCTIVE FAIL** — [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64), no automatic retry |
| LineageOS original ZIP SHA-256 not equal to official; Recovery signature error 21 | **INSTALL FAIL due to mismatched source ZIP**; cannot infer USB transport fault |
| OrangeFox R11.3 ZIP sent from Lineage Recovery; then new OrangeFox Recovery enumerated | **Recovery switch FIELD OBSERVED; ZIP installer exit code UNKNOWN**, not unconditional INSTALL PASS |
| Same device candidate [PR #71](https://github.com/Ncorror/NekoFlash-2.0/pull/71) | **CI PASS**; on exact test build `6.0.0-alpha11+808babac31f2.42576443632`, **partial FIELD PASS**: Evolution X Sideload 2,282,083,364 bytes with Recovery `operation_end status=0`; Mass Flash `boot/dtbo/vbmeta` final OKAY 3/3; later Android 14 boot confirmed by tester photo. Not full PR acceptance (e.g. ZIP selection persistence untested) | 
| New **256 MiB ADB Pull** on this PR #71 build, OrangeFox Recovery and booted Android 14 | **FIELD FAIL**: Recovery 2× and Android 1× `WRTE/INVALID_PAYLOAD` ([#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72)). Same file's remote SHA observed in both modes; initial source SHA comparison not shown. Error currently masks underlying USB-IN/checksum diagnostic cause |

No flash/format was ever performed on the **other locked vayu**. Original private logs and photos remain private. Do not repeat Mass Flash/format on this TARGET to re-prove historical tests; investigate the failures instead.

---


<img src="artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# Physical-Device Validation

### Hardware testing results, qualification matrix, and device evidence

English

</div>


## Current evidence index — 8 November 2026 (post-REV54)

**Authoritative detailed ledger:** [English](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) · [Russian](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). This section reconciles recent physical acceptance with the older alpha10/REV45 snapshots below. **Do not confuse build-specific PASS with all-devices approval.**

| Pair | APK scope | Real result | Limits |
| --- | --- | --- | --- |
| POCO F7 HOST → **unlocked** POCO X7 Pro rodin | historical alpha10 | ADB, Quick Flash active/all slots; two **real 64 MiB `vendor_boot_a/b`** writes, Recovery/Sideload classification, final Android boot and post-fix five shell commands **PASS** | Sideload's standalone transfer status was **VERIFY_PENDING**, the subsequent system boot is separate evidence; not current DEV |
| POCO F7 HOST → **unlocked** POCO X3 Pro vayu Android 14 | alpha10.1 | ADB/reconnect, Fastboot, **128 MiB real recovery flash**, Recovery, **three Sideload packages Recovery-verified SUCCESS**, Fastbootd and final Android boot **PASS** | Summary retained `verificationPending=3` despite later Recovery evidence; not three install failures |
| POCO F7 HOST → **unlocked** POCO X3 Pro vayu Android 14 | **REV48**, 7 Nov | Another **128 MiB recovery flash** (`flash:recovery OKAY`), Recovery boot, Sideload detection and Fastbootd mode **PASS** | Separate **OrangeFox-R11.3-vayu.zip 73,757,208 bytes stayed `VERIFY_PENDING`**; no proven installation result and no independent recovery readback |
| POCO F7 HOST → a **different locked** POCO X3 Pro vayu Android 12 | **REV54**, 7–8 Nov | 1023/1024/1025-byte WRTE boundaries with SHA; fresh **512/512 recursive push+pull**, 16 MiB byte-exact roundtrip **PASS** | Initial pre-fix push failed at 223 files (1024-byte boundary); fixed only this tested USB pairing. **No flash, format, wipe or unlock on the locked TARGET** |

Additional **REV53** hardware checks on unlocked vayu Android14 completed 10/10 short shell-v2 commands (8 early-CLSE buffered replies) and a separate post-polish 2/2; ZIP byte-level manifests passed. REV51/52 ZIP byte integrity checks passed for the files captured, **but the inherited REV48 missing log/trace prologue remains unrecovered** ([#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50)). Chrome TestBook `content://` export had a physical FAIL ([#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49)); APK SAF ZIP export was a different path.

**Integrated source vs physical acceptance:** REV54 ZLP issue [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) was closed only after hardware evidence and green REV55 merge CI [#502](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327). The later scenario-only PR #62 merged into [`main=6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e); [post-merge CI #42517047929](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42517047929) **SUCCESS**, but merging a TestBook does not execute 43 physical tests or establish new destructive acceptance.

**Remaining:** on-device physical verification of Chrome JSON/ZIP export and progress restore, previous log-history loss, stale Sideload result after force-stop, remaining separate collapse-animation/rare landscape IME viewport (the **physical terminal output/header rotation regression already PASSED on Nov 7**, [PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45)), intermittent DocumentsUI and targeted memory stress; keep these open and avoid blanket re-running the already accepted H3 USB corpus. **Operation Center visual redesign is deferred by project decision**.

---

## Historical release and REV45 snapshots (dated evidence, not the current source)

## REV45 hardware beta · 2 November 2026

The reference APK is from [main CI #413](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886), commit `a31dedadd4ad`. Both emulator jobs passed. A verified physical HOST → TARGET report for REV45 is still pending.

Use the [Insiders kit and RU/EN guides](insiders/README.md). Record both devices, Android versions, cable, build ID, test ID, result and reproduction steps. Priorities are OTG/RSA, terminal keyboard and rotation, 512-file ADB push, Stop and reconnect. Keep raw evidence within the trusted team. The older alpha10 results below apply only to their original builds.

---


This document is the public index of NekoFlash physical-device testing. It contains only hardware-validation facts that are useful for release qualification. Raw diagnostic logs, USB traces, account data, device serials, private photographs, chat/continuation notes, recovery archives, temporary plans and operator-state files are intentionally not part of the public repository.

## Test host

The 2026-08-10 qualification sessions used a POCO F7 as the Android USB host. NekoFlash reported the host platform as Xiaomi `25053PC47G` / `onyx`, Android 16 (SDK 36).

## Qualification summary

| Target | Product | Evidence scope | Result |
|---|---|---|---|
| POCO X7 Pro | `rodin` / model `2412DPC0AG` | ADB, Fastboot, unlock history, A/B inventory, real `vendor_boot` flashing, Quick Flash slot selector, Recovery/Sideload classification, final boot, post-fix ADB shell lifecycle | **PASS** |
| POCO X3 Pro | `vayu` | exact release-base regression: repeated ADB shell/reconnect, bootloader Fastboot, real `recovery` flash, Recovery/Sideload, Fastbootd, final boot | **PASS with reporting note** |

Detailed evidence:

- [POCO X7 Pro (`rodin`) hardware validation](evidence/POCO_X7_PRO_ALPHA10.md)
- [POCO X3 Pro (`vayu`) release-base hardware validation](evidence/POCO_X3_PRO_ALPHA10_1.md)

## POCO X7 Pro release evidence

The `rodin` campaign contains both the earlier alpha10 baseline and the final hotfix qualification.

Hardware-confirmed items include:

- canonical ADB and Fastboot connectivity;
- real Xiaomi unlock flow and persistent unlocked state after reconnect;
- A/B partition discovery;
- real 64 MiB `vendor_boot` payload transfer and flash;
- Fastbootd/userspace detection;
- ROM/GApps/Recovery Sideload transport;
- post-P4 Quick Flash slot selector UI;
- real all-slot `vendor_boot_a` + `vendor_boot_b` flash;
- P3 close-before-`DONEDONE` classification as `VERIFY_PENDING` instead of transport failure;
- final Android/Evolution X boot;
- reproduction of the one-shot ADB shell stream lifecycle regression;
- post-fix H13 with repeated shell commands and `NEKOFLASH_ALPHA10_FINAL_OK`, all exit code 0.

The final H13 verification used build `6.0.0-alpha10+3d3e85e5fce1.31369769922`. Five ADB operations started, five succeeded, zero failed, and no warning/error remained in the session summary.

## POCO X3 Pro release-base regression

The `vayu` campaign used build `6.0.0-alpha10+de1225b9cd5b.31373308470`, which corresponds to the merged release-base commit `de1225b9cd5be110c72c6f130320b2d62d927cc0`.

Hardware-confirmed items include:

- repeated `shell_v2` commands and reconnects after the ADB CLSE fix;
- final marker `NEKOFLASH_ALPHA10_FINAL_OK` with exit code 0;
- ADB-to-bootloader transition and Fastboot handshake;
- real 128 MiB `recovery.img` flash through Native USBFS;
- ADB Recovery and ADB Sideload mode detection;
- three real sideload workflows with Recovery-side success evidence;
- ADB-to-Fastbootd transition and userspace Fastboot detection;
- final boot into Evolution X / Android 14.

The session summary recorded zero failed operations. Three sideload operations remained counted as `verificationPending` even after Recovery-side success evidence was collected. This is retained as a reporting/bookkeeping cleanup item, not a demonstrated installation failure.

## Release qualification boundary

The evidence has two complementary levels:

1. **Hotfix-path hardware qualification on POCO X7 Pro (`rodin`).** The P4 slot-selector path and P3 Sideload classification were exercised on build `34ca8302...`; the ADB shell lifecycle fix was then validated on build `3d3e85e...`. Those fixes were subsequently merged into the release-base history.
2. **Exact merged release-base regression on POCO X3 Pro (`vayu`).** Build `de1225b9cd5b...` exercised the merged transport line across ADB, Fastboot, Recovery, Sideload and Fastbootd.

This distinction is intentional: a component-level hotfix test must not be represented as if it were an exact-head run, and an exact-head run on another device must not be represented as a replacement for device-specific `rodin` evidence.

## Public evidence policy

Only sanitized conclusions and non-unique device metadata belong in GitHub documentation. Do not commit:

- raw USB/Fastboot traces;
- device serials or unique identifiers;
- account names, phone numbers, cookies, tokens or RSA material;
- private screenshots/photos containing account information;
- chat/continuation/recovery-context documents;
- backup archives or temporary operator-state reports.

The private validation archive may retain those materials for local audit and recovery, but it is not a public repository artifact.
