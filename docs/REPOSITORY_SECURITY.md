# NekoFlash 2.0 — repository and release security

## Verified on 13 November 2026

- Release environment `nekoflash-release` is real and approval-gated: the alpha11 signed release remained in GitHub's waiting deployment state until the owner approved it.
- The signed release workflow completed successfully and verified the permanent certificate before and after APK build.
- Build/release third-party Actions are pinned to full commit SHAs.
- Release source provenance and exact-SHA CI gates are enforced in workflow code.
- Repository rulesets API returned **no rulesets** (`[]`) during this audit.
- Branch-protection details could not be read by the connected GitHub App (403: integration permission), so this repository must **not** claim branch protection was independently verified by the audit.

## Source-enforced release gates

The workflow requires:
- exact `versionName` ↔ `v$versionName`;
- release tag commit == current `main`;
- exact-SHA successful main CI before tag/signing;
- protected `nekoflash-release` environment before signing secrets;
- permanent certificate SHA-256 `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`;
- APK certificate re-verification after build;
- APK/filesystem verification before GitHub Release publication.

## Remaining manual GitHub Settings hardening

The source repository cannot create administrative rulesets through the connected GitHub App. The owner should configure this in **Settings → Rules → Rulesets**.

For `main`:
- require a Pull Request before merge;
- require the real CI checks selected from a successful main run;
- block force pushes;
- restrict branch deletion;
- require conversation resolution if desired.

For a solo maintainer, do not require an approval that the author is technically unable to provide unless a second trusted reviewer is available.

For `v*` tags:
- restrict updates and deletion;
- if tag creation is restricted too, explicitly allow the trusted release workflow/admin path so the automated exact-main release flow is not broken.

After enabling rulesets, perform a non-destructive verification with an ordinary PR. Do **not** create a fake production `v*` tag just to test the rule; the tag path is a real publish path.

## Secrets

Signing secrets belong only in `nekoflash-release`, not in ordinary CI. Never commit a JKS, Base64 keystore or passwords.

The successful alpha11 release proves the configured environment had the required release values at that time; GitHub does not expose secret contents for audit.
