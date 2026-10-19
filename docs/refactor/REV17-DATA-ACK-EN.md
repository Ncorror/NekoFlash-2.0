# REV17 — verify Fastboot DATA response size

Date: 2026-10-19. Base: PR #2 / REV16.

## Defect

After `download:%08x`, the bootloader sends `DATAxxxxxxxx`, where the eight hexadecimal digits indicate the number of bytes it expects. Previously any DATA packet started USB payload transmission. A smaller, larger or malformed size could desynchronize the transport and leave the outcome ambiguous.

## Fix

- New `FastbootDataReplyPolicy` checks the exact eight-hex-digit wire format and matches it against the requested payload length.
- The check occurs **before** the first USB payload write.
- Covers `flashPartitionDetailedInternal` (including direct SAF/Native USBFS), `downloadAndRun` and existing `stageAndOemUnlock`.
- Malformed or mismatched DATA marks the session broken. Further commands require Fastboot reconnection; **no blind retry**.
- `stageAndOemUnlock` also applies the existing wire/device download-size policy before sending download.
- Existing working features, package and workspace paths are unchanged.

## Regression gates

- JVM: `FastbootDataReplyPolicyTest`: exact match, wire max, short/oversized reply, zero size and invalid hex.
- `tests/check_rev17.py`: chains REV16 and checks that all three paths validate prior to writing USB payload.
- GitHub build and release workflows run the structural, bilingual, JVM, lint and build gates.

**Not hardware-verified.** Run only safe getvar and file-selection checks first on the dedicated unlocked test device. Destructive writes and forced USB-failure scenarios need an independent recovery plan.

Out of scope: sparse chunk splitting, Mass Flash SAF, UI redesign and any OEM restriction bypass.
