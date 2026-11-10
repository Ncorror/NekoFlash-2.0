# NekoFlash 2.0 — Release readiness audit · 10 November 2026

## Candidate identity

- Repository: `Ncorror/NekoFlash-2.0`
- Current main: `0faca68e302b8007f340926025f7672cb9e6b3cb`
- Android version: `6.0.0-alpha11`
- versionCode: `234`
- Production application ID: `ru.forum.adbfastboottool`
- Debug application ID: `ru.forum.adbfastboottool.dev`
- minSdk 26, compileSdk 36, targetSdk 34 (targetSdk is intentionally pinned in source; GitHub APK distribution is the current release path).

## Exact-main CI

GitHub Actions [run #42736593646 / #647](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42736593646) on the exact main SHA is **SUCCESS**.

Verified jobs:
- build — SUCCESS;
- installed Android API 26 — SUCCESS;
- installed Android API 34 — SUCCESS;
- Package verified Insiders tester kit — SUCCESS;
- pin field kit job — SUCCESS.

Build evidence:
- 338 JVM tests, 0 failures, 0 errors, 0 skipped;
- Android Lint: 0 Warning/Error/Fatal findings;
- instrumentation contract expects and passed 28 tests on each API26/API34 matrix;
- repository security/source guards and native transfer policy tests passed.

## Current tester kit

Run #647 artifact `NekoFlash-Insiders-Tester-Kit`, artifact ID `13727327902`.

Local integrity verification of the downloaded artifact:
- ZIP CRC: PASS;
- exactly 2 files;
- ZIP SHA-256: `73ad2b92003a7ce515b3936800e13e5e1a5b36107e771f3d973a6031240bc61b`;
- `NekoFlash-6.0.0-alpha11-debug.apk`: 10,933,968 bytes, SHA-256 `b89a321ac5fea7e4162f1163c2bb94e4a79d5e80c36c5e9fa0e76899c6c2efc1`;
- `testbook.html`: 164,084 bytes, SHA-256 `a3e74547dfd361e72ef2f3247fe2013d074d4ffcd1549c657cac2b35615a9a30`;
- packaged HTML sourceCommit: `0faca68e302b8007f340926025f7672cb9e6b3cb`;
- packaged HTML runId: `42736593646`;
- embedded APK SHA matches the actual APK.

The static TestBook still carries the historical campaign label `REV49` / `insiders-rev49`. Its packaged build provenance is current and exact. This audit intentionally does **not** change `campaign-tag.txt`, because doing so would publish a new Insiders prerelease automatically.

## Release signing gate

The source is fail-closed for production release packaging:
- `assembleRelease/packageRelease/bundleRelease` fail if the permanent signing inputs are absent;
- manual Actions signing runs only on `main` with `workflow_dispatch` + `sign_release=true`;
- the keystore certificate is checked against pinned SHA-256 `b122576dcaa5b1ceebd977545314bd515432f9e3fa50733fa8153a1e48a6c19e`;
- the completed APK is independently verified by `apksigner`;
- the APK certificate digest is checked again against the same pinned fingerprint;
- the release artifact refuses filenames containing `unsigned`.

**Not yet verified on this exact candidate:** no `NekoFlash-0faca68e...-release` artifact exists in run #647 because ordinary push CI does not execute the manual signing path. Therefore production alpha11 is not yet ready to publish until a signed exact-final-SHA run succeeds.

## Integrated release changes confirmed in main

- Explicit Sideload selection and confirmed Start is merged by PR #74; choosing a ZIP no longer mutates the target.
- Lossless diagnostic log storage/current ZIP integrity fixes are integrated; issue #50 is closed.
- Recursive ADB push no longer materializes the whole tree; issue #36 is closed.
- Long-shell retention work is closed under #19.
- The intermittent API26 CI investigation #16 is closed.
- vayu F2FS format transport/filesystem scope #65 is resolved/closed.
- 9 Nov hardware campaign #66 and Sideload safety #69 are closed.
- Existing physical PASS evidence for Mass Flash, Recovery, terminal rotation, REV53 shell, REV54 ZLP/512-file/16 MiB remains valid and is not invalidated by this audit.

## Explicitly excluded from alpha11 candidate

Open issues are intentionally post-release/backlog:
- #20 — terminal collapse / rare landscape+IME follow-up;
- #46 — Operation Center visual redesign;
- #47 — field confirmation of stale completed strip after force-stop;
- #48 — intermittent Android DocumentsUI root behavior;
- #64 — vayu-specific `fastboot -w` / `erase:cache` final-response compatibility;
- #72 — large ADB Pull EPROTO.

Open post-release PRs:
- #67 — fail-closed fastboot-w regression test branch;
- #68 — Recovery replacement observation without claiming installer success;
- #73 — ADB Pull diagnostic branch.

None of those branches is merged into the candidate. Do not merge #73 for release.

## Repository hygiene

The repository still contains many old backup/docs/fix/test branches. Most checked historical REV46–55/docs branches are strictly behind main with zero unique commits; several experimental branches are diverged. Branch deletion is **not** part of this audit and requires a separate cleanup decision. The branch set does not change the content of `main`.

## Final gate before publication

1. Merge this documentation-only release-readiness update after green CI.
2. On the resulting exact final `main` SHA, run **Build Android APK → Run workflow → sign_release=true** in the protected `nekoflash-release` environment.
3. Require all jobs to pass and verify the uploaded `NekoFlash-<final-sha>-release` artifact contains the signed `NekoFlash-6.0.0-alpha11-release.apk`, `apk-signature.txt`, `build-info.txt` and checksum.
4. Confirm certificate SHA-256 equals the pinned permanent fingerprint.
5. Perform only a **non-destructive signed-APK smoke check**: install/upgrade, launch, tabs, selected Sideload remains non-mutating, no unexpected operation on startup. Do not repeat destructive Mass Flash/format/wipe merely because the APK is signed.
6. Then publish/tag alpha11 with release notes derived from `CHANGELOG.md`.

Until step 3 succeeds, status is **SOURCE/CI RELEASE CANDIDATE — SIGNED PRODUCTION ARTIFACT PENDING**.
