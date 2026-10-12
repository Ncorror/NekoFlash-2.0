# NekoFlash 2.0 — full REV14 audit

Date: 2026-10-12  
Audited base: PR #2 / `feature/rev11-core-cleanup` after REV13.  
Status: source audit plus fixes; **not** a successful hardware flashing report.

## Summary

No P0 issue was found that obviously permits a blind Fastboot retry after an ambiguous USB failure or converts UNKNOWN verification into SUCCESS. The core Fastboot/Native USBFS and Recovery-verification safety invariants remain intact.

Several P1 items still block merge and the final hardware claim.

## Architecture

- `MainActivity.kt`: ~4343 lines and still a God-object.
- `DeviceViewModel.kt`: ~1894 lines.
- `AdbProtocol.kt`: ~2806 lines.
- `FastbootProtocol.kt`: ~2481 lines.
- Decomposition has started with ConsoleDockController, TabController, FirmwareSource and WorkspaceImportNaming but is incomplete.

### REV14 fixes

- Remove dead hidden Welcome checkbox/notification/battery/status controls and obsolete persisted risk state.
- Keep OnboardingGate as a process/task-only barrier against exported USB-intent bypass. Storage permission never blocks entry.
- Remove arbitrary queue URI/display-name caps and all silent metadata truncation.
- Stop rejecting uppercase / ':' manual partition targets solely because of a GUI regex.
- Remove unused DiagnosticLogStore maxSegmentsPerStream.
- Replace stale `-rev6-dev` suffix with stable `-dev`.
- Require structural/i18n and JVM tests in the signed Release workflow before publication.

## P1 before merge

1. **Interactive ADB PTY** — service is `shell,v2,pty:`, but input is still line-oriented and appends newline. True character-by-character IME/paste/backspace support is not complete.
2. **RU/EN** — resource parity is enforced, but legacy user-visible logs still contain English Kotlin literals.
3. **Large Fastboot images** — single `download:%08x` is 32-bit. Correct max-download-size-aware sparse/chunk splitting is required; deleting the size check would be incorrect.
4. **Mass Flash SAF** — queue remains local file://; persisted content:// grants and process-death restoration are still missing.
5. **Native USBFS descriptor hardware test** — REV13 builds, but dup(fd)/fstat/pread/URB with Android DocumentsProvider has not yet been proven on the device.

## Real safety/protocol boundaries — keep them

- <=64-byte Fastboot wire command.
- 32-bit `download:%08x` payload representation.
- Serialized USB mutation / transaction ownership.
- Native USBFS drain + poison, and no blind retry inside an ambiguous DATA session.
- Source readability/size and exact target validation.
- Recovery UNKNOWN never becomes SUCCESS.
- Signed release requires the permanent verified certificate.

## Paths and storage

Approved paths remain unchanged: `ru.forum.adbfastboottool`, source tree under the same namespace, workspace `/sdcard/Download/NekoFlash`.

MANAGE_EXTERNAL_STORAGE is only for legacy workspace File operations. Direct SAF Sideload/Quick Flash uses only the document chosen by the user.

## UI

REV10 addressed the device screenshots (terminal trigger wrapping, tab contrast, Welcome clutter). REV14 physically removes the hidden legacy Welcome tail. Re-test Welcome, five bottom tabs, Quick Flash, Sideload and fullscreen Terminal/IME on a real screen.

## Diagnostics/privacy

Historical segments are not automatically deleted; compact and trace logs are separated. Share sanitization masks serial/account/device identifiers, private paths and long identifiers. Mi Account logs contain cookie names, not values. Signing secrets remain in GitHub Secrets.

## CI/release

Before REV14 the normal build executed structural/i18n/JVM/lint/APK checks, while the tag Release workflow did not repeat structural/JVM regression gates. REV14 closes that gap.

## P2 cleanup

Continue extracting USB presentation, source selection, Fastboot UI, reports/history/settings and Mi Unlock from MainActivity; then split ViewModel orchestration and remove compatibility shims only after a stable hardware cycle.

## Big-test gate

Require green REV14 structural + i18n + JVM + Android lint + Debug APK and a non-destructive device smoke test (Welcome/storage, RU/EN, USB enumerate, ADB connect, Fastboot getvar, SAF selection without writes, terminal/IME) before any destructive test.
