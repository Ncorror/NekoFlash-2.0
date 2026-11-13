# NekoFlash 2.0 — hardware validation

**Current release qualification:** `v6.0.0-alpha11`, accepted 13 November 2026.

This document is the current verdict. Detailed chronological FAIL → fix → PASS evidence is retained in the [EN physical ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) and [RU ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md).

## Qualified results

| Host → target | Scope | Verdict |
| --- | --- | --- |
| POCO F7/onyx → unlocked POCO X3 Pro/vayu | ADB/Fastboot/Recovery/Sideload, real Fastboot writes, format/wipe campaign | PASS within the individual boundaries below |
| POCO F7/onyx → unlocked POCO X7 Pro/rodin | historical alpha10 ADB/Fastboot/Quick Flash/recovery campaign | PASS for documented builds |
| POCO F7/onyx → locked POCO X3 Pro/vayu | non-destructive ADB boundary/recursive transfer tests | PASS; no flash/format/unlock performed on this locked target |
| POCO X3 Pro/vayu host → POCO X3 Pro/vayu target | framework.jar Pull; repeated 128 MiB Pull/Push + SHA-256 | PASS |

## Final #64 format/wipe acceptance

On unlocked vayu:

- `fastboot format userdata` generated/transferred F2FS successfully.
- Android subsequently reached a fully booted state.
- `fastboot -w` erased/recreated userdata/cache and handled raw metadata as erase-only.
- a legitimate long `erase:metadata` response (~17.7 s) was waited out without resend/disconnect;
- `fastboot reboot` returned OKAY;
- the phone reached the Evolution X initial setup screen after wipe.

**Verdict: FIELD PASS.**

Earlier format/wipe failures remain historical evidence; they are not the current release verdict.

## Final #72 ADB Pull disposition

On POCO F7/onyx host, long inbound ADB traffic can intermittently fail with a real USB receive error (`EPROTO/USB_IN_FAILED`). Equivalent target/files pass on an X3 Pro host, and Legacy NekoFlash recorded the same class of intermittent onyx-host receive behavior.

Production robustness:
- large normal-Android Pull is split into 256 KiB durable ranges;
- only complete ranges are fsynced;
- a failed packet/range is not replayed blindly;
- after manual reconnect, the same Pull can resume from the durable checkpoint;
- final file publication requires source recheck and SHA-256 equality.

**Verdict:** generic Pull path FIELD PASS; POCO F7/onyx documented as a host-specific compatibility case.

Power/VBUS sensitivity is plausible field context but **not proven**, because voltage/current were not instrumented.

## Sideload boundary

Selecting a ZIP is non-mutating. Transfer starts only after explicit Start/confirmation. Transfer completion alone is not automatically an install-success verdict; Recovery evidence controls installation classification. Near-complete Recovery disconnects can correctly remain `VERIFY_PENDING`.

## Qualification rule

A PASS is scoped to the tested device, firmware, build and operation. Do not generalize one phone's result to every USB controller/bootloader. Do not rerun destructive scenarios merely to reproduce already accepted evidence unless code/hardware scope changes.
