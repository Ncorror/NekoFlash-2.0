# REV13 — direct SAF for Quick Flash (EN)

**Status:** new transport path implemented, still needs real-device verification on a dedicated already-unlocked Poco X3 Pro. This is **not** a successful flashing test report.

## Unchanged

- Production package `ru.forum.adbfastboottool`, Kotlin source directory `app/src/main/java/ru/forum/adbfastboottool/`, workspace `/sdcard/Download/NekoFlash`.
- Existing `File`-based Fastboot calls for terminal, mass queue, and download-and-run commands.
- Native USBFS URB pipeline, cancellation, confirmed-byte accounting, failed-drain poisoning and no blind retry of ambiguous DATA operations.
- Never silently change transport during a DATA phase or treat completion of a transfer as proof that a partition was written.

## New path

The existing Quick Flash image button offers two choices: an Android SAF document or an existing local workspace image. SAF supports `content://`, a persisted read grant when available, a seekable read-only descriptor and validated `fstat`. No image copy into the workspace is required solely for Quick Flash.

`FirmwareSource.Opened.nativeFd` remains valid until all Fastboot DATA and final-response steps finish. Native USBFS now accepts the original filename **or** a borrowed descriptor and calls `dup(fd)` before the existing `pread` + URB pipeline. Native still owns its duplicate until drain is complete.

If Native USBFS is unavailable, a provider is pipe-only, document size is unknown, or seek fails, the direct SAF mode declines before sending Fastboot DATA; the user receives a bilingual error and may select a local image. No automatic transfer-mode switch is introduced.

## Not implemented yet

- Direct SAF in the **mass flash queue** and reliable persisted URI-grant handling after process recreation. The queue retains its original `File` storage format.
- Big RAW/sparse transfers: current Fastboot `download:%08x` encodes a 32-bit single payload length. It must not be bypassed by dropping the limit without correct chunking/sparse protocol support.
- Remaining legacy Kotlin text localization and gradual `MainActivity` decomposition.

## Device test gate

First do the non-destructive smoke test on an unlocked test device: read Fastboot `getvar`, choose a small Android document, check correct filename and **no additional workspace copy**, choose an old workspace image, switch RU/EN and verify permissions. No real `flash`, unplug during DATA, slot changes, erase, update-super or format without explicit approval, backups, correct images and a recovery plan.

CI compilation is not a replacement for native-transfer hardware validation. Keep PR in Draft and leave `main` unchanged.