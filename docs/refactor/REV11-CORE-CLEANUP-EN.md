# REV11 — Core cleanup, file access and RU/EN localization

Status: active refactor. Original base: the tested NekoFlash 6.0.0-alpha11 (**not NekoFlash Pro**). **Do not rename packages or paths.**

[Русская версия](REV11-CORE-CLEANUP-RU.md)

## Current implementation

- Production package and namespace: `ru.forum.adbfastboottool`. Existing Kotlin source folder: `app/src/main/java/ru/forum/adbfastboottool/`. DEV APK uses the `.dev` application ID suffix so it can coexist with the signed original.
- Workspace is and remains `/sdcard/Download/NekoFlash`, currently created by `MainActivity.initWorkspace()`. Do not rename it or relocate user files.
- The existing file picker flow still requires workspace initialization and copies Android SAF `content://` data into the workspace before operations; the command backends accept `java.io.File`. Direct URI/FD streaming is a separate task, not a completed feature.
- Legacy limits: REV7 removed the 32-item flash queue cap and unwanted sorting. REV8 retained historical diagnostic segments. REV11 removed silent truncation of the UI operation steps (240) and fastboot inventory warning logs (4).
- `MainActivity` and `DeviceViewModel` remain large. Their responsibilities should be split gradually without rewriting proven USB/ADB/Fastboot transports.

## Artificial limitations to remove

- Mandatory workspace copying for every permitted Android document just to run a flash or sideload operation. Keep the workspace location unchanged, but separate `WorkspaceLocation` from `FirmwareSource` so content URIs/FDs can be used directly when the underlying protocol supports them.
- Silent caps on operation history, queue entries or diagnostics, implicit command reordering and redundant UI actions.
- Duplicate file-selection/import code and obsolete hidden compatibility views **after** checking that no active handler still depends on them.

## Safety requirements that must remain

- Exclusive USB ownership and serial operation execution where required.
- USBFS URB drain/poison, correctly identifying the physical target, and no blind retries after an unknown Fastboot result.
- Actual read permissions, valid target commands, accurate status reporting and device verification on reconnect.

## RU/EN language contract — approved 10 October 2026

- Every new user-visible button, dialog, error, progress status, instruction and accessibility label must have Russian and English resources.
- English defaults live in `app/src/main/res/values/strings.xml`; Russian strings live in `values-ru/strings.xml`. No locale-only keys or incompatible formatting parameters.
- Runtime locale switching among system / RU / EN already exists. `tests/check_i18n.py` checks resource parity, format placeholders, locale configuration and the unchanged package/workspace names.
- Preserve technical command syntax, partition identifiers, USB wire responses, paths, keys and schema identifiers literally in both languages.
- Existing hardcoded English status messages in Kotlin still need incremental localization; the resource parity test alone does **not** prove that all UI/log output has been translated.

## Next work, without path migration

1. Split firmware sources from workspace storage; add the SAF grant/FD path, with explicit fallback copy only when needed by seek/random access.
2. Isolate Navigation, USB status, Firmware/Flash workflows, Terminal, Diagnostics and Mi Unlock from the activity without replacing the verified transports.
3. Remove unused UI glue after call-site checks and build/regression tests. Do not reintroduce previously rejected Sideload or Unlock controls.
4. Run bilingual screen/device tests, Android CI, SAF providers, terminal IME/PTY and safely prepared large-file tests before merge.

**Scope boundary:** this document records decisions and partial refactor progress, not successful hardware testing or completion of all localization.

## Next REV11 localization pass

- Moved **24 additional user-facing messages** from Kotlin literals to paired EN/RU resources: file imports, unchanged workspace paths, USB device state, diagnostics and several Fastboot errors.
- Extended the localization contract test to require both languages, actual Kotlin references, and absence of the replaced English-only messages.
- No behavior or permission changes to Fastboot commands, USB transports, package identifiers, or workspace paths.
- **Remaining work:** other legacy string literals, notably terminal, USB recovery and flashing failure messages. Full localization still requires follow-up edits and bilingual device testing.
