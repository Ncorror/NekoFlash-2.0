<div align="center">

<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash Changelog

### Release-level changes and version history

English

</div>

The released history below is preserved. **Unreleased NekoFlash 2.0 DEV changes are not published APK releases.** The technical REV number is independent of `versionName` / `versionCode`; see [development status](docs/DEVELOPMENT_STATUS.md). Historical build/release statements refer to the source snapshot in which they were recorded.

## Unreleased NekoFlash 2.0 DEV — REV38–43 (1 November 2026)

**Not a signed production APK release.** REV26–44 DEV work was integrated into `main` for hardware beta testing via [PR #39](https://github.com/Ncorror/NekoFlash-2.0/pull/39), merge commit `34990ea14f21e908f777a0fb22715d1ff399ee81`. Historical DEV base: `work/rev26-terminal-imports-20261030`, functional REV43 merge `bd0d1fbf0ff5ba69634df5281fa87e2ff1289bf5`. Published legacy NekoFlash release assets remain separate; the `main` source is **not** a new official release.

- REV38: Gboard composing deletion, Unicode and emoji Backspace; [PR #31](https://github.com/Ncorror/NekoFlash-2.0/pull/31).
- REV39–40: native terminal output text selection/copy and avoid unexpected autoscroll while selecting; [PR #32](https://github.com/Ncorror/NekoFlash-2.0/pull/32) / [PR #33](https://github.com/Ncorror/NekoFlash-2.0/pull/33).
- REV41: cancellable Fastboot post-DATA final-response polling (30 s overall, 2 s slices); [PR #34](https://github.com/Ncorror/NekoFlash-2.0/pull/34).
- REV42: generic ADB `A_WRTE` respects negotiated peer maxdata, including SYNC data frames; [PR #35](https://github.com/Ncorror/NekoFlash-2.0/pull/35).
- REV43: lazy recursive `adb push -r` with all-directory-before-file order and cancellation retained across nested file writes; [PR #37](https://github.com/Ncorror/NekoFlash-2.0/pull/37).
- REV44 documentation/test tooling: [offline RU/EN testbook](docs/insiders/testbook.html) with eight additional field checks, original AutoLogs BIN ZIP/export, [RU](docs/testing/INSIDERS-FIELD-TEST-REV43-RU.md) / [EN](docs/testing/INSIDERS-FIELD-TEST-REV43-EN.md) field manuals and CI HTML validation/artifact publication.

**Evidence limits:** [REV43 PR CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391) passed build, API26 and API34. The [merged DEV CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147) passed build and API34 but **failed API26 on two UI wait timeouts**. Physical DEV REV43 HOST→TARGET validation has not yet been recorded as PASS; older alpha10 tests do not count. See [current status](docs/DEVELOPMENT_STATUS.md).

## Historical Unreleased DEV (REV26–REV27, based on `6.0.0-alpha11`)



- REV26: separated terminal close from cancellation and operation progress; preserved command history and PTY; fixed cancellable SAF provider import on Android API 26.
- REV27: deferred RecyclerView updates while Console is collapsed, rendered up-to-date output on reopen, and added regression tests for long output.
- [Verified REV27 CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41012595076) passed 19 installed-app tests on API 26 and API 34. These changes **are not** a production release and do not validate physical-device flashing.

## Unreleased `6.0.0-alpha11` source line — UI/UX audit

- Restored and refined the Home **Operation Center** as a persistent, non-blocking task surface with a prominent current action, percentage/indeterminate progress, `step X of Y`, a bounded nearby-step queue, contextual failure/verification details, reports access and explicit cancellation.
- Added a compact **live operation strip** above every workflow page so Flash/Sideload/Unlock/diagnostic work remains visible without leaving the current tab. It now shows the current step together with the safety/status message, and tapping it opens Home and scrolls directly to the full Operation Center. Completed/cancelled results auto-clear after a short confirmation window, while failed/verify-pending states remain until reviewed or dismissed.
- Hardened operation-state presentation so lightweight diagnostics/commands cannot revive a previous heavy operation result, including after cancellation or Activity recreation. Cancellation now has an explicit safe-cleanup state with indeterminate progress, inactive cancel controls are disabled, and Activity recreation preserves both an in-flight cancellation state and a user-dismissed result strip.
- Changed Console behavior so operations never auto-expand or preview it; starting an operation returns Console to its compact dock, while the user can still open it explicitly at any time.
- Restored visible Console command-history controls while the sheet is expanded and added accessibility descriptions for expand/collapse and icon-only import actions.
- Removed dead UI tails: the obsolete flash progress overlay, permanently hidden legacy multi-flash queue UI, unreachable workspace-folder/inventory dialogs, and their unused styles/resources.
- Completed a RU/EN UI-copy pass for Mi Unlock, Quick Flash slot selection, logs/reports, device overview and Operation Center labels, preserving technical protocol payloads unchanged.
- Kept USB transport, ADB/Fastboot protocol logic, network flows, mutation algorithms and operation ownership unchanged.

## v6.0.0-alpha10.2 — published release

- Historical release snapshot identity was `versionName=6.0.0-alpha10.2`, `versionCode=233`; the current NekoFlash 2.0 DEV source has since advanced to `versionName=6.0.0-alpha11`, `versionCode=234`.
- The UI/UX audit above is **unreleased** and is not claimed as part of the already published alpha10.2 APK.

## v6.0.0-alpha10.1 — published hotfix release

- Fixed the Quick Flash slot-selection dialog so slotted partitions expose explicit active-slot and all-slots choices on hardware.
- Fixed ADB one-shot shell stream close handling so stale `CLSE` packets from older stream IDs do not break subsequent shell commands.
- Physically qualified the late alpha10 hotfix paths on POCO X7 Pro (`rodin`), including the Quick Flash slot selector, real `vendor_boot_a` / `vendor_boot_b` flashing, P3 Sideload classification, final Android boot and post-fix H13 ADB shell lifecycle.
- Ran an exact merged release-base regression on POCO X3 Pro (`vayu`) across ADB, bootloader Fastboot, real Recovery flashing, Recovery/Sideload, Fastbootd and final Android boot.
- Retained the POCO X3 Pro Sideload `verificationPending` counter and one post-success stream-close log as reporting/severity cleanup notes rather than misreporting them as installation failures.
- Published GitHub release assets under `v6.0.0-alpha10.1`; the APK embedded version remains `versionName=6.0.0-alpha10`, `versionCode=232`.

## 6.0.0-alpha10 (232) — source line

- Added Quick Flash slot-target selection: when a partition reports `has-slot=yes`, the user can choose the current slot or all reported slots; explicitly suffixed `_a` / `_b` targets remain concrete.
- Added desktop-fastboot-compatible `--slot` parsing for terminal `flash`, `erase` and `format`, including `--slot=all` expansion from device-reported slot information.
- Improved ADB Sideload completion handling so a near-complete transport close can remain pending Recovery verification instead of being reported immediately as a hard failure.
- Replaced the blocking flash progress dialog with a non-modal Operation Status panel while keeping Terminal & Logs available during long operations.
- Improved Fastboot terminal output: visible `OKAY` / `FAIL` / `INFO` responses and returned `getvar` values are shown in Console.
- Improved Xiaomi unlock token parsing for multi-line `oem get_token` INFO responses.
- Updated Unlock UI state so the unlock action is hidden when Fastboot already reports `unlocked=yes`.
- Clarified the Home bootloader status label and localized boolean values.
- Consolidated transport bookkeeping and progress calculations without changing the wire protocol.
- Preserved fail-closed production signing with permanent certificate verification in local scripts and GitHub Actions.
- Cleaned the public repository documentation: removed temporary planning/state/continuity material and consolidated permanent build, architecture, release and Termux guidance into `README.md`.

## 6.0.0-alpha8 (222)

- Hardened Fastboot numeric size parsing against overflow.
- Made operation brightness handling idempotent and restoration predictable.
- Aligned release scripts with the signed-release-only invariant.
- Added local keystore certificate verification and post-build APK signature verification.

## 6.0.0-alpha7 (220–221)

- Restored the persistent ADB/Fastboot terminal in Console.
- Simplified Home into a device/transport dashboard and streamlined Quick Flash and Mi Unlock UI.
- Reported missing ADB/Fastboot connections and invalid Fastboot tokens inline in Console.
- Preserved raw/OEM Fastboot passthrough for connected devices.

## 6.0.0-alpha6 (218, development line)

- Hardened Native USBFS/JNI ownership with RAII and bounded URB lifetime handling.
- Added transfer progress based on confirmed URB completions and overflow-safe calculations.
- Improved ADB reboot completion handling around expected USB teardown.
- Added structured diagnostic severity and improved compact/session logging.
- Performed broad source, warning and layout cleanup while preserving physical USB transport behavior.

## 6.0.0-alpha5 (217, development line)

- Introduced Recovery-first Quick Flash and partition/topology-aware target selection.
- Added Fastboot DATA lifecycle work and legacy A-only device handling.
- Hardened Xiaomi account/unlock URL and cookie scoping.
- Improved onboarding, sideload UX and device-smoke diagnostics.

## 6.0.0-alpha4 (216)

- Restored pending unlock and sideload verification state required by the active Android build.
- Fixed Android compilation regressions in the V6 cleanup line.

## 6.0.0-alpha3 (215)

- Completed the first V6 source cleanup after removing the legacy full Mi Flash workflow.
- Removed old service/profile/history layers and raw hardware-log artifacts from the active source tree.

## 6.0.0-alpha2 (214)

- Added the Home device/workspace card and navigation to Terminal, Quick Flash, ADB Sideload and Mi Unlock.

## 6.0.0-alpha1 (213)

- Started the V6 line by removing the legacy full Mi Flash workflow.
- Retained direct ADB/Fastboot transport, Terminal, Quick Flash, ADB Sideload, Mi Unlock and sanitized diagnostics.
