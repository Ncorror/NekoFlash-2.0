# NekoFlash 2.0 documentation

This directory intentionally separates **current operational documentation** from historical evidence.

## Current sources of truth

- [STATUS.md](STATUS.md) — current release/main state and known limitations.
- [AUDIT_2026-11-13.md](AUDIT_2026-11-13.md) — post-release repository audit and its verified findings.
- [HARDWARE_VALIDATION.md](HARDWARE_VALIDATION.md) — current hardware qualification boundary.
- [USB_CONNECTION_TROUBLESHOOTING.md](USB_CONNECTION_TROUBLESHOOTING.md) — current USB/OTG troubleshooting.
- [SECURITY.md](SECURITY.md) — application security/privacy model.
- [REPOSITORY_SECURITY.md](REPOSITORY_SECURITY.md) — CI, signing and GitHub administration state.
- [LEGAL_REVIEW.md](LEGAL_REVIEW.md) — licensing/distribution checklist.
- [PROFESSIONAL_RECOVERY_POLICY.md](PROFESSIONAL_RECOVERY_POLICY.md) — raw diagnostic and expert-operation policy.
- [../PRIVACY.md](../PRIVACY.md) and [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

## Evidence, not instructions

- [testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) and the [RU ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md) retain a compact chronology of physical FAIL → fix → PASS evidence.
- [evidence/](evidence/) contains older device-specific release evidence.
- [design-approved/](design-approved/) contains historical approved mockups/decisions. They are not current implementation instructions.

Old REV/refactor/source-audit snapshots were removed from the active tree after the alpha11 release. They remain available through Git history when forensic comparison is actually needed.

## Public site

The GitHub Pages source is under this directory. Public guides must describe the current behavior, not an old PR candidate. The documentation CI checks local links and rejects known stale public references.
