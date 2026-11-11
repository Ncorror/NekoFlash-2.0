# NekoFlash 2.0 — Release readiness audit · 10 November 2026

## Verified Insiders REV50 release — 11 November 2026

**Publication type:** Android-to-Android hardware testing **prerelease/debug**. **NOT** production-ready signed alpha11. Published [Insiders REV50](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/insiders-rev50) from the exact `main` source [`e7dde419`](https://github.com/Ncorror/NekoFlash-2.0/commit/e7dde419fc66f4be8427d7be002fd239e5d06446), `6.0.0-alpha11` / versionCode `234`.

**Verified chain of custody:**
- [GitHub Actions #682](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42930623047) completed **SUCCESS** on `e7dde419fc66f4be8427d7be002fd239e5d06446`: build, Android API26, Android API34, verified Insiders kit packaging and prerelease publishing all succeeded.
- The published asset [`NekoFlash-Insiders-REV50.zip`](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev50/NekoFlash-Insiders-REV50.zip) has GitHub SHA-256 digest `df36aacd679b8fd040136ab8fb18b329eb0ebb8ba0e3ca56d89101fb1a700073` — **identical** to workflow artifact `NekoFlash-Insiders-Tester-Kit` (artifact `13816517910`). The workflow verifier requires exactly an APK and `testbook.html`, bound to the same commit/run and APK checksum.
- `docs/insiders/campaign-tag.txt` was advanced to `insiders-rev50` by existing commit `e7dde419`. Keep that tag **unchanged** for ordinary documentation updates. The older `insiders-rev45` release is historical, not a current build.
- The release also includes the separate `filesystem-tools-source.tar.gz` source archive for compliance; it is **not** an extra file inside the two-file tester kit.
- PRs #79–#82 are already integrated. [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83) remains **open and excluded**: its errno discriminator is diagnostic and has no field-proven ADB Pull transport fix.

**Known limitations and truth in labeling:**
- [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72): large inbound ADB Pull can fail on the POCO F7 HOST → POCO X3 Pro TARGET pair; no complete source/destination SHA-256 evidence, **FIELD FAIL / FIX PENDING**. Ordinary ADB Shell/Push successes do not qualify Pull.
- [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64): `fastboot -w` / `erase:cache` and unanswered bootloader `reboot` require further target-specific evidence. Fastboot confirmed `OKAY` for some erase/format commands but bootability is separate.
- [#20](https://github.com/Ncorror/NekoFlash-2.0/issues/20), [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47), [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48): UI/force-stop/DocumentsUI field follow-up. [#46](https://github.com/Ncorror/NekoFlash-2.0/issues/46) visual Operation Center work remains explicitly deferred.
- Only the original tested hardware pair and observed firmware/kernel combinations can be described as FIELD PASS; no universal claim of USB compatibility.

**Safe release handoff / non-destructive smoke on a real Android host:**
1. Download only the tagged REV50 ZIP above, confirm its ZIP SHA-256, extract the matching debug APK and `testbook.html`, and check the embedded APK digest. Keep the previous DEV logs/TestBook progress; never uninstall or clear data without backing them up.
2. Install/launch the debug `ru.forum.adbfastboottool.dev` build and inspect version/build ID. Check Home, terminal input/rotation/IME, operation visibility, diagnostic export, and opening TestBook in Chrome.
3. With a consenting spare target and USB debugging authorized, verify USB enumeration and non-destructive `adb shell getprop ro.product.model`. Check detach/reconnect and report transport status. Do **not** flash/erase/format/wipe/unlock/sideload merely to clear release checkboxes.
4. Capture target/host Android versions, kernel/ROM and private diagnostic export for failures. For issue #72, use one separately agreed non-destructive Pull of an existing file and match source/destination SHA-256 before calling the Pull fixed.
5. Record real-device smoke result as **FIELD PASS / FAIL / NOT RUN**; GitHub CI success alone is insufficient.

**Production publication gate (still PENDING):** choose a final `main` SHA, require its complete CI; build a **permanent-key signed** release APK via protected `nekoflash-release`, verify the pinned cert `b122576dcaa5b1ceebd977545314bd515432f9e3fa50733fa8153a1e48a6c19e` and APK signature, perform a non-destructive smoke on **that exact signed build**, then authorize a version tag/`v*` publishing workflow. No production release tag or signed-production claim has been made as part of this Insiders handoff.

## Candidate identity

- Repository: `Ncorror/NekoFlash-2.0`
- Current application-code baseline before final documentation sync: `6a13d8ed2ff806faf06d85e637f942e89eb6c4bf`
- Important: merging release documentation advances `main` and changes the APK's embedded `BUILD_COMMIT`, even when Android source is unchanged. The final signed artifact must therefore be built from the final tagged SHA.
- Android version: `6.0.0-alpha11`
- versionCode: `234`
- Production application ID: `ru.forum.adbfastboottool`
- Debug application ID: `ru.forum.adbfastboottool.dev`
- minSdk 26, compileSdk 36, targetSdk 34 (targetSdk is intentionally pinned in source; GitHub APK distribution is the current release path).

## Application-baseline CI

GitHub Actions [run #42804598658 / #653](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42804598658) on the exact application baseline is **SUCCESS**.

Verified jobs:
- build — SUCCESS;
- installed Android API 26 — SUCCESS;
- installed Android API 34 — SUCCESS;
- Package verified Insiders tester kit — SUCCESS;
- pin field kit job — SUCCESS.

Build evidence:
- 347 JVM tests, 0 failures, 0 errors, 0 skipped;
- Android Lint: 0 Warning/Error/Fatal findings;
- instrumentation contract expects and passed 28 tests on each API26/API34 matrix;
- repository security/source guards and native transfer policy tests passed.

## Audited current tester kit

Run #653 artifact `NekoFlash-Insiders-Tester-Kit`, artifact ID `13757708292`, is the verified application-baseline kit entering the final docs sync.

Local integrity verification of the downloaded artifact:
- ZIP CRC: PASS;
- exactly 2 files;
- ZIP SHA-256: `a4773158fa903e145fea66bd28d86addafcd3702007ebaf5df63c7140cfe9aaf`;
- `NekoFlash-6.0.0-alpha11-debug.apk`: 10,938,304 bytes, SHA-256 `46230d199aa4eba93cb219b878c6395de9fd41ca1bfa2c042d096b663410f677`;
- `testbook.html`: 164,084 bytes, SHA-256 `b57d22fc9dffec453759716838db5adad8b7a9a7ec2bcfdd3f0f824ecfa74f01`;
- packaged HTML sourceCommit: `6a13d8ed2ff806faf06d85e637f942e89eb6c4bf`;
- packaged HTML runId: `42804598658`;
- embedded APK SHA matches the actual APK.

After the documentation merge, do not reuse the APK hash above as the final-main hash: `GITHUB_SHA` is embedded in the APK, so the next successful main run will produce a different binary digest. Always use the hash embedded in the tester kit from that exact run.

The static TestBook still carries the historical campaign label `REV49` / `insiders-rev49`. Its packaged build provenance is current and exact. This audit intentionally does **not** change `campaign-tag.txt`, because doing so would publish a new Insiders prerelease automatically.

## Release signing gate

The source is fail-closed for production release packaging:
- `assembleRelease/packageRelease/bundleRelease` fail if the permanent signing inputs are absent;
- manual Actions signing runs only on `main` with `workflow_dispatch` + `sign_release=true`;
- the keystore certificate is checked against pinned SHA-256 `b122576dcaa5b1ceebd977545314bd515432f9e3fa50733fa8153a1e48a6c19e`;
- the completed APK is independently verified by `apksigner`;
- the APK certificate digest is checked again against the same pinned fingerprint;
- the release artifact refuses filenames containing `unsigned`.

**Not yet verified on this exact candidate:** no signed release artifact exists in run #653 because ordinary push CI does not execute the manual signing path. Therefore production alpha11 is not yet ready to publish until a signed **final-SHA** run succeeds.

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

Release-hardening integration:
- #67 fail-closed `fastboot -w` regression coverage is merged through PR #76;
- #68 Recovery replacement observation is merged through PR #76 and remains `VERIFY_PENDING` without a proven installer result;
- #73 ADB Pull diagnostics were closed without merge and remain excluded from alpha11.

Do not merge #73 for release.

## Repository hygiene

Historical backup/docs/fix/test branches were audited before cleanup. Release content is preserved in `main`, merged PR history, issues, and CI artifacts; experimental ADB Pull work remains documented under #72/#73 but is not part of alpha11. Branch cleanup is performed only after the final docs merge and its post-merge CI, retaining `main` as the working branch.

## Final gate before publication

1. On the exact final `main` SHA, require the normal build/JVM/lint/API26/API34/tester-kit CI to pass before production signing.
2. On the resulting exact final `main` SHA, run **Build Android APK → Run workflow → sign_release=true** in the protected `nekoflash-release` environment.
3. Require all jobs to pass and verify the uploaded `NekoFlash-<final-sha>-release` artifact contains the signed `NekoFlash-6.0.0-alpha11-release.apk`, `apk-signature.txt`, `build-info.txt` and checksum.
4. Confirm certificate SHA-256 equals the pinned permanent fingerprint.
5. Perform only a **non-destructive signed-APK smoke check**: install/upgrade, launch, tabs, selected Sideload remains non-mutating, no unexpected operation on startup. Do not repeat destructive Mass Flash/format/wipe merely because the APK is signed.
6. Then publish/tag alpha11 with release notes derived from `CHANGELOG.md`.

Until step 3 succeeds, status is **SOURCE/CI RELEASE CANDIDATE — SIGNED PRODUCTION ARTIFACT PENDING**.
