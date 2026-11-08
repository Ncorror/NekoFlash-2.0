# NekoFlash 2.0 — evidence ledger for tests already executed

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
