# NekoFlash 2.0 — evidence ledger for tests already executed

## Issue #72 audit — ADB Pull series, 9–10 November

**Rig:** POCO F7 / onyx Android 16 HOST → unlocked POCO X3 Pro / vayu TARGET, tested in OrangeFox Recovery and booted Evolution X Android 14. Raw diagnostics remain private; only sanitized evidence is recorded publicly.

- Original Java ADB-IN: **FIELD FAIL** on 256 MiB Pull in Recovery and Android; diagnostic revision exposed `USB_IN_FAILED` (one packet stopped at 1,015,808 / 1,048,576 bytes). Push remained successful.
- One 1 MiB synchronous Java bulk read: **FIELD FAIL** at 0/1,048,576, `-1`, ~39 ms.
- Java `UsbRequest` four-by-16 KiB window: **FIELD FAIL**, first completion 0/16,384, ~3 ms.
- `CNXN.maxdata=16 KiB` + original Java reader: framing change physically worked and Pull reached 50% (134,217,728 / 268,435,456), but then failed at 0/16,384. Separate lifecycle fix **FIELD PASS**: reader failure now ends promptly as FAILED instead of remaining RUNNING and becoming CANCELLED.
- HOST kernel dmesg showed DWC3/xHCI active and a later explicit disconnect; no simultaneous disconnect/reset/stall was proven at the app-level failure. The filtered log cannot reveal the Java bulk failure's kernel errno.
- Final [PR #73](https://github.com/Ncorror/NekoFlash-2.0/pull/73) candidate: **CI PASS / FIELD PENDING**. ADB payload IN now uses a dedicated native usbfs URB reader (`USBDEVFS_SUBMITURB/REAPURB`) retaining errno, `urb.status` and `actual_length`; ADB headers retain the proven Java path. Partial packets are never published, checksum remains mandatory, no command auto-retry, pending URB is discard+reap on failure, uncertain drain poisons the backend fail-closed. ADB Push/OUT, Fastboot, Sideload, firmware and log retention are unchanged.
- Exact head `e3ac779d0593f26ce181e84e44773d488145b758`: [CI #42705941131](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42705941131) **SUCCESS attempt 1**, including strict source guards, native C++ warnings-as-errors, JVM, lint, Debug APK and installed Android API 26/34.
- Final acceptance requires one physical 256 MiB Pull using the [matching tester kit](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42705941131/artifacts/13714112372), followed by source/returned SHA-256 equality. No reflash, wipe, Sideload or repeat Push is required.

---


## 9 November late field recheck (14:19–14:22), same test APK PR #71

**Build:** `6.0.0-alpha11+808babac31f2.42576443632`; CI [#42576443632](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42576443632) PASS, **not merged into main**.

- **Evolution X 14.0 vayu sideload INSTALL PASS:** 2,282,083,364-byte ZIP, `DONEDONE` followed by current-attempt Recovery `operation_end status=0`; **boot into Android 14 tester-confirmed** from a subsequent phone photograph. The older LineageOS bad-ZIP/format/wipe failures below remain separately valid.
- **Mass Flash `boot/dtbo/vbmeta`: FIELD PASS 3/3** via final Fastboot OKAY. This is separate from the earlier `boot/dtbo/recovery` mass flash on a different build.
- **ADB Push 256 MiB:** succeeded once in OrangeFox Recovery and again in booted Android; the TARGET SHA-256 is the same in both modes. **Original HOST file hash has not been independently supplied.**
- **ADB Pull 256 MiB:** failed **twice in Recovery and once in Android** with `WRTE INVALID_PAYLOAD` (1,048,576 bytes; one Recovery retry at 422,256). This disproves a Recovery-only explanation; root cause remains unknown. Details in [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72).
- **ZIP audit:** 87/87 and 30/30 entries matched export SHA-256 and byte sizes; CRC PASS, 18/18 JSON parse and 26/26 raw shell metadata/stream pairs. These are complete *snapshots of the included files*, not proof of all prior history.
- **Code audit:** `readCompletePacketDirect` replaces specific `readDataDirect` USB-IN/checksum error codes with generic `INVALID_PAYLOAD`; second summary calls the transport-terminated operation `CANCELLED`. Preserve fail-closed transport and raw logs; improve diagnostics before another targeted Pull test.
- **Recovery /data at 14:04:** `/metadata` was mounted ext4 but `/data` was not mounted, and `df /data` resolved to rootfs; `ro.crypto.state=encrypted`. This is a snapshot **before** the successful ROM installation, not proof of an ongoing Android mount problem.

**Current qualification:** combined PR #71 has **partial real-hardware evidence**, not full UI lifecycle acceptance (ZIP persistence/restart unverified). Do not rewrite the historical early-9-Nov rows below.

---

> **Update, 9 November 2026:** this addendum records tests executed **after** the original 8 November snapshot. Preserve earlier H1/H2/H3 evidence; do not automatically repeat real destructive tests.

## 9 November field evidence — H2, unlocked A-only vayu

**HOST:** POCO F7 onyx Android 16. **TARGET:** unlocked POCO X3 Pro vayu Android 14, **A-only**, *not* the separate locked vayu. Actual installed APK: `6.0.0-alpha11+6a8accd8e33e.42517047929`, not the later PR test candidate. Raw ZIPs and device photos remain private.

| Operation | Actual status | Interpretation |
| --- | --- | --- |
| Mass Flash `boot` 128 MiB, `dtbo` 32 MiB, `recovery` 128 MiB | **FIELD PASS 3/3**, true Fastboot final OKAY | 00:10:50–00:10:58; Recovery subsequently booted. Does not certify installed Android boots. |
| `getvar current-slot`, `slot-count` | **Not available** | A-only target returns `FAIL Variable Not found`; no invented A/B slots. Fastbootd transition independently PASS. |
| `fastboot format userdata` (F2FS) | **Host transfer OKAY, Android boot FAIL** | `mkfs.f2fs` 1.16, `flash:userdata OKAY`, then `init_user0_failed` and no Android boot. Root cause not established; [#65](https://github.com/Ncorror/NekoFlash-2.0/issues/65). |
| `fastboot -w` | **PARTIAL DESTRUCTIVE FAILURE** | `erase:userdata` and `flash:userdata` OKAY; `erase:cache` sent, **no final packet** for ~57/85s, cancelled. `flash:cache` and `erase:metadata` never executed. [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64); [PR #67](https://github.com/Ncorror/NekoFlash-2.0/pull/67) CI PASS regression test protects fail-closed behavior, not root-cause repair. |
| LineageOS ZIP Sideload | **INSTALL FAIL, original ZIP SHA-256 mismatch** | Local `1e41d63e6bd60816a3174d7e16913c44389ca0981c9339840326bc4f0fab28a7` vs official `91d62ab3746841dc0ead83e36be458b390ef018b94afd8d579d4270f9a0dce21`; Recovery `Signature verification failed`, `error:21`. The old file must not be installed or bypassed. Mismatch origin unknown. |
| OrangeFox R11.3 ZIP via **Lineage Recovery Sideload** | **Data served; Recovery changed; installer exit UNKNOWN** | 00:23:09 ZIP 73,757,208 B closed near 100% before `DONEDONE`; 00:24:11 device booted different Recovery banner `lineage_vayu` → `vayu` (USB `18D1:D001` → `2717:D001`), OrangeFox pictured. Do not attach an unrelated `E1001 system image update` screenshot to this ZIP installer as a proven failure. |
| Private diagnostic ZIPs | **Manifest integrity PASS** | `1794172866747.zip` 23/23, `1794173155618.zip` and `1794175939025.zip` 39/39; does not guarantee lost earlier events were restored. |

**New candidate, not yet on device:** [PR #71](https://github.com/Ncorror/NekoFlash-2.0/pull/71) combines **non-mutating ZIP selection + explicit confirmed Start** with **Recovery change observation without false INSTALL SUCCESS**. [Combined CI #42533760615](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42533760615) **SUCCESS**: 346 JVM tests, 0 failures, zero Lint findings, Android API 26/34 checks, two-file tester kit. PR head `7e5ac5bc57220370f8404dc501f81c4828986ba2`, GitHub synthetic merge/test SHA `198e521bd78df191e7fe3c77651a1df0169ea995`; `main` remains `6a8accd8e33e`. No physical acceptance for the combined APK.

**Next evidence, not an automatic destructive rerun:** examine correct downloaded ROM SHA-256 when it exists; inspect current real Android boot/Recovery outcome; later verify the new Sideload UI on the existing target *without launching a flash when only selecting the file*. [#66](https://github.com/Ncorror/NekoFlash-2.0/issues/66), [#69](https://github.com/Ncorror/NekoFlash-2.0/issues/69) remain open until hardware verification. Do not publish original raw logs.

---

**Snapshot:** November 8, 2026, `main` source [`6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e), merged PR #62. This is an **evidence index, not a new checklist**. [Detailed RU ledger](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md).

## Evidence classification

- **FIELD PASS:** operation actually performed on a named physical HOST → TARGET pairing and a recorded APK build. It is not transferable to other devices or later APKs without a scoped regression.
- **CI PASS:** automated build/JVM/lint/emulator checks on a specific Git commit. Not proof of on-device USB/flash behavior.
- **FIELD FAIL:** reproduced on physical hardware. Keep it as historical evidence even after later fixes.
- **VERIFY_PENDING / PARTIAL:** the transfer or some UI state occurred, but the real recovery/installation outcome was not independently proven.
- **NOT VERIFIED / BLOCKED:** no conclusive on-device result or insufficient safe equipment/backup. A TestBook item does not become PASS merely because it was added to the list.

## Distinct physical hardware

| Pair | Hardware | Scope |
| --- | --- | --- |
| H1 | HOST POCO F7 / onyx Android 16 → unlocked POCO X7 Pro / rodin | historical alpha10, real A/B `vendor_boot` flashing |
| H2 | HOST POCO F7 / onyx Android 16 → unlocked POCO X3 Pro / vayu Android 14 | historical alpha10.1, Nov 7 REV48 physical recovery/fastbootd/sideload, REV51–53 shell and ZIP evidence |
| H3 | HOST POCO F7 / onyx Android 16 → a **different, locked** POCO X3 Pro / vayu Android 12 | Nov 7–8 REV54 USB ZLP acceptance; disposable ADB files only |

**Never report a flash on H3.** All actual recovery flashes in this evidence happened on unlocked H2 or older approved hardware.

## Physical checks that must not be reintroduced as “never tested”

| Real operation | Observed result / exact build scope | Source |
| --- | --- | --- |
| H1 ADB/RSA/shell, unlock flow and A/B Fastboot selection | FIELD PASS, alpha10 | [rodin evidence](../evidence/POCO_X7_PRO_ALPHA10.md) |
| H1 real `vendor_boot_a` and `vendor_boot_b` writes, **64 MiB each**, Quick Flash all-slots | FIELD PASS on `34ca8302` | [rodin evidence](../evidence/POCO_X7_PRO_ALPHA10.md) |
| H1 Evolution X Sideload payload 3,889,931,331 bytes, close before DONEDONE | Transfer classification correct, **VERIFY_PENDING**, later Android boot FIELD PASS; pending alone is not installation proof | [rodin evidence](../evidence/POCO_X7_PRO_ALPHA10.md) |
| H1 ADB H13 initial shell regression, then five 0-exit commands | FIELD FAIL then 5/5 FIELD PASS on fixed build `3d3e85e5` | [rodin evidence](../evidence/POCO_X7_PRO_ALPHA10.md) |
| H2 ADB, reboot to Fastboot, 128 MiB real `recovery.img` flash, Recovery, Sideload mode, Fastbootd, final Android 14 boot | FIELD PASS on alpha10.1 `de1225b9` | [vayu alpha10.1 evidence](../evidence/POCO_X3_PRO_ALPHA10_1.md) |
| H2 three Sideload packages: firmware (~103 MiB), Evolution X (~2.18 GiB), DerpXtreme (~14.6 MiB) | **Recovery-verified FIELD PASS** on alpha10.1; the session summary still said `verificationPending=3` (reporting gap) | [vayu alpha10.1 evidence](../evidence/POCO_X3_PRO_ALPHA10_1.md) |
| H2 real 128 MiB recovery Fastboot DATA + `flash:recovery OKAY`, then booted Recovery/Fastbootd | FIELD PASS by protocol plus working Recovery, Nov 7 **REV48** `79c8c0a9`; no independent partition readback | [REV48 field report](../insiders/FIELD-REPORT-2026-11-07-ONYX-VAYU-RU.md) |
| H2 REV48 OrangeFox R11.3 ZIP, 73,757,208 bytes | **VERIFY_PENDING**, no DONEDONE and no conclusive linked Recovery install verdict; do not mark PASS | [REV48 field report](../insiders/FIELD-REPORT-2026-11-07-ONYX-VAYU-RU.md), [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47) |
| H2 REV53 short ADB shell-v2 | **10/10 FIELD PASS**, 8 early CLSE recovered, 35/35 ZIP hashes; post-INFO polish **2/2 FIELD PASS**, 46/46 hashes | [#52](https://github.com/Ncorror/NekoFlash-2.0/issues/52), [REV53 details](../source-audit/REV53-SHELLV2-EARLY-CLOSE-2026-11-07-RU.md) |
| H3 original 512-file recursive push | **FIELD FAIL at 223/512**, file 224 = 1016-byte data / 1024-byte SYNC WRTE; isolated 1016 case failed 3/3, adjacent sizes passed | [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) |
| H3 REV54 boundary USB transfers 1023/1024/1025 | **FIELD PASS**, target/source SHA-256, fixed candidate `6cb2233fcdc7`, CI #494 | [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) |
| H3 REV54 recursive push **and** pull of 512 files | **FIELD PASS 512/512**, complete file/hash check, fresh destination, original failed partials retained | [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) |
| H3 REV54 16 MiB push/pull | **FIELD PASS**, 16,777,216 bytes, target SHA-256 and HOST byte comparison | [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) |

## Real diagnostics and UI observations

- **REV51 ZIP:** 55/55 manifest file sizes/hashes passed. Status remains `INCOMPLETE_OR_UNVERIFIED` because an inherited REV48 log+trace session prologue was missing and two atomic JSON paths changed during export. **Not proof of complete event history.** [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50).
- **REV52 ZIP:** 103/103 hashes passed; `ATOMIC_SUMMARY_REPUBLISHED` note replaced false active-summary mutation warnings, while the inherited missing REV48 prologues remained. **REV52 short echo still failed 3/3**, repeat echo failed 6/7, later fixed in REV53. [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50), [#52](https://github.com/Ncorror/NekoFlash-2.0/issues/52).
- **Terminal rotation/IME:** REV29 and REV48 physical failures were reproduced. **Nov 7 physical retest PASS for the previously shifted output/header after screen rotation**: the tester reported no recurrence, provided a screenshot, and the result was recorded in [PR #45 at 12:52 UTC](https://github.com/Ncorror/NekoFlash-2.0/pull/45). The user reports three test submissions; their individual build hashes are not proven by that one public comment. **Do not reassign the same rotation test.** Separate collapse-animation timing and an intermittent emulator landscape+IME zero-height viewport condition remain narrower issues, not evidence that this field PASS never happened. [#20](https://github.com/Ncorror/NekoFlash-2.0/issues/20), [REV48 original FAIL](../insiders/FIELD-REPORT-2026-11-07-ONYX-VAYU-RU.md).
- **Operation Center after force-stop:** stale 100% VERIFY_PENDING strip reproduced; source fix later integrated, **on-device re-acceptance not documented**. UI redesign deliberately deferred. [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47), [#46](https://github.com/Ncorror/NekoFlash-2.0/issues/46).
- **TestBook on Chrome `content://`:** copy and JSON/ZIP saving **FIELD FAIL** on REV48; the APK's own SAF raw ZIP export worked, a different mechanism. New HTML fallbacks are source/CI checked but not field accepted. [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49).
- **DocumentsUI:** intermittent empty root on HOST, later populated without a proven source change; root cause open. [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48).
- **Full source inventory:** PR #61's optional read-only `--check-source-inventory` is CI-verified, not yet a complete physical source-vs-ZIP acceptance. Historic missing first events remain unresolved. [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50).
- **ADB large output/heap and recursive tree memory stress:** code may have changed but full physical memory retention/stress qualification is not documented. [#19](https://github.com/Ncorror/NekoFlash-2.0/issues/19), [#36](https://github.com/Ncorror/NekoFlash-2.0/issues/36).

## CI-only and development status

- REV55 candidate [CI #501](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42426015449): 336 JVM, lint 0, Android API26 28/28 plus independent same-SHA rerun, API34 28/28, tester kit PASS. Merged [CI #502](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327) passed. This also closed the specific H3 ZLP regression [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57). Broader intermittent tests [#16](https://github.com/Ncorror/NekoFlash-2.0/issues/16) remain open.
- PR #60/#61 documentation and source-inventory checks merged; their CI passed.
- PR #62 updated **43 TestBook cases including six LAB-01..06 plans**, and [candidate CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42515445165) passed; merged at [`6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e). Independent post-merge [main CI #42517047929](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42517047929) finished **SUCCESS**: build, Android API26/34, tester-kit packaging and pinning. This does not certify LAB hardware operations. **No LAB destructive operation was executed by creating those six cards.**

## Original private ZIP checks, 8 November

The first draft of this audit relied excessively on existing GitHub comments and overlooked the tester's **original uploaded archives**. We recovered and examined them locally. **No private log/trace bytes were committed to GitHub.** Full per-archive mapping with builds, session outcomes and limitations is in the [RU source-evidence appendix](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md).

- REV48: `1794047242102.zip`, 49 entries and `1794048915556.zip`, 23 entries, both CRC PASS but without a modern export manifest. The first includes a physical terminal failure on the earlier `79c8c0a9d7ac` build (6 operations, 4 succeeded, 2 failed). The second has the historically **missing** first log/trace lines (8/6/2); ZIP CRC alone did not restore evidence.
- REV51 `1794056370360.zip`: CRC PASS, **55/55** manifest SHA-256 and sizes, but status **INCOMPLETE_OR_UNVERIFIED**; 14 operations, 8 succeeded / 6 failed on build `18afb2c45cf6`.
- REV52 `1794064372351.zip`: CRC PASS, **103/103** SHA-256, **INCOMPLETE_OR_UNVERIFIED**; 17 operations, 8 succeeded / 9 failed on build `cbe6cc87942f`.
- REV53 `1794079211850.zip`: CRC PASS, **46/46** SHA-256, **CONSISTENT_SNAPSHOT**; two sessions: **10/10** on `b44792605c98` and **2/2** on `c48f8eae8009`.
- REV54 `1794090330941.zip`, `1794090783149.zip`, `1794091135463.zip`, `1794091500167.zip`: CRC PASS; manifest checks respectively **31/31, 23/23, 65/65, 11/11**; all have **CONSISTENT_SNAPSHOT**. Their session outcomes include intermediate 7/8, later 4/4 and 4/4; preserve the intermediate failure without denying later successful evidence.
- `NekoFlash_Hardware_QA_20261107.zip`: CRC PASS, 519 original QA corpus entries. This proves source test files were supplied, not by itself that any device transfer succeeded.

The terminal output/header rotation bug has an independent physical acceptance note with screenshot on [Nov 7, PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45#issuecomment-6707179253). **Do not reassign that same test.** The operator reports three submissions, but one public note alone is insufficient to fabricate three separate exact-build outcomes.

File integrity **does not equal** complete original event history or full app-private source-directory inventory. Continue tracking [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50).

## How to decide the next tests

Do not treat historic TestBook checkboxes as an inventory of missing coverage. Keep evidence and original logs intact. Only prepare a new test when the code or hardware actually changed, or when an existing result is ambiguous/missing; state that difference explicitly. The next focused campaign will be agreed **in chat**, using this ledger. No new destructive commands should be blindly copied from generic historic templates; only unlocked expendable hardware with verified off-device backups and recovery is eligible. Do not delete historical TestBook reports or publish raw device traces.
