# REV16 — real Fastboot limits and max-download-size

REV16 removes misleading host behavior without deleting actual protocol boundaries.

## Changes

- Payload size is preflighted before `flash` and `download + command`.
- The real single-command `download:%08x` boundary remains: one payload cannot represent >= 2^32 bytes.
- If the target reports `getvar:max-download-size`, NekoFlash honors the device's smaller bootloader limit before entering DATA.
- If max-download-size is unavailable, NekoFlash does not invent a smaller arbitrary cap; the bootloader remains authoritative.
- Desktop-fastboot `-S/--sparse-limit` is no longer silently ignored with a false “not required” message. Until proper sparse/chunk splitting exists, the command is not sent and the user gets a bilingual explanation.

## Why 4 GiB is not an artificial restriction

The current Fastboot wire command carries eight hex digits of size: `download:%08x`. Removing validation would produce an invalid/overflowed command, not large-image support. Proper support requires max-download-aware sparse/chunk generation.

## Unchanged

Native USBFS drain/poison/no-blind-retry, REV13 SAF fd transfer, package/workspace paths and Mass Flash order remain unchanged.

## Tests

`FastbootDownloadSizePolicyTest` covers wire/device limits and absent device limits. REV16 structural tests verify preflight before DATA and ensure `-S` cannot be silently consumed.

Do not run large-image destructive tests until the small-image hardware smoke gate is complete.
