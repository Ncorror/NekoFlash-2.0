# NekoFlash 2.0 — physical evidence ledger

This is a compact chronology of evidence that materially affects current behavior. It is **not a test assignment list**. Earlier low-level REV notes remain available in Git history and closed issues/PRs.

## 13 November 2026 — release acceptance

### Fastboot format/wipe (#64)

Final unlocked-vayu sequence:
- standalone `fastboot format userdata`: F2FS image generated/transferred, Fastboot OKAY, later Android `sys.boot_completed=1`;
- `fastboot -w`: userdata erase/recreate, raw metadata erase-only, cache erase/recreate, final reboot OKAY;
- `erase:metadata` legitimately took about 17.7 s; NekoFlash waited without resend/disconnect;
- device reached the Evolution X initial setup screen after wipe.

**Final verdict: FIELD PASS.**

Earlier format/wipe boot failures were real historical failures and drove the final fixes; they do not override the later accepted PASS.

### Large ADB Pull (#72)

POCO F7/onyx host repeatedly reproduced real inbound USB failures (`USB_IN_FAILED/EPROTO`) at varying offsets. Changing timeouts/URB sizes did not make the failure deterministic or eliminate it. Legacy NekoFlash history on the same host contained similar intermittent inbound failures.

Cross-host control on POCO X3 Pro host → POCO X3 Pro target:
- `/system/framework/framework.jar` Pull: 39,293,777 bytes, final SHA-256 verified;
- generated 128 MiB test file Pull: PASS;
- 128 MiB Push: PASS;
- target hash after Push matched source;
- second 128 MiB Pull: PASS with the same hash.

**Final verdict:** generic ADB Pull path FIELD PASS. POCO F7/onyx is a host-specific compatibility case.

Current robustness uses 256 KiB durable Pull ranges, manual reconnect resume and final SHA-256 verification. Failed packets are not replayed.

## 11–12 November — Sideload and Fastboot acceptance

On unlocked vayu / OrangeFox:
- ZIP selection became non-mutating; explicit Start is required; selection can be cleared;
- Recovery/Sideload USB transitions were observed and repeated;
- OrangeFox update/reboot behavior was observed on real hardware;
- near-complete Recovery disconnects are preserved as `VERIFY_PENDING` when installer success is not independently proven;
- Fastboot bootloader transition, native Fastboot handshake, reboot and subsequent ADB/recovery reconnect passed.

The accepted policy is deliberately conservative: transport completion does not fabricate Recovery install success.

## 7–8 November — transport and UI regressions

- A 1024-byte ADB boundary failure was reproduced on locked vayu, fixed, then verified with neighboring 1023/1024/1025 wire sizes and recursive 512-file Push/Pull.
- Terminal output/header rotation regression was reproduced and later physically rechecked as PASS.
- Short shell-v2 early-close failures were reproduced, fixed and rechecked.
- No flash/format/wipe/unlock was performed on the locked vayu used for non-destructive boundary tests.

## Older alpha10 evidence

Device-specific alpha10 evidence remains in [../evidence/](../evidence/). Those files are historical qualification records for their exact builds, not current release instructions.

## Evidence rules

- Keep FAIL → fix → PASS history; do not rewrite an earlier real failure into a PASS.
- Keep `VERIFY_PENDING` distinct from SUCCESS and FAIL.
- A CI PASS is not a physical hardware PASS.
- A transfer percentage is not an installation verdict.
- Raw device logs/photos may remain private; public documentation should contain sanitized conclusions and non-unique metadata.
