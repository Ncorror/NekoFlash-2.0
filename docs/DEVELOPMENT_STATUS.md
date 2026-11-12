# NekoFlash 2.0 — current development status

**Updated:** 13 November 2026

## Release candidate

- Version: `6.0.0-alpha11`
- versionCode: `234`
- Production applicationId: `ru.forum.adbfastboottool`
- Debug applicationId: `ru.forum.adbfastboottool.dev`
- Release source of truth: `main`; the signed release is bound to the exact release tag commit.
- Release signing certificate SHA-256: `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`

## Final hardware disposition

- #64 Fastboot wipe/format compatibility — **closed, FIELD PASS** on unlocked vayu after `format userdata`, `fastboot -w`, metadata/cache handling, reboot and Android setup boot.
- #72 large inbound ADB Pull — **closed**. Cross-host control on X3 Pro host passed repeated 128 MiB Pull/Push with matching SHA-256. POCO F7/onyx is an intermittent host USB receive compatibility case; merged production behavior adds 256 KiB durable checkpoints and final SHA verification without retrying failed USB packets.
- Recovery Sideload, Mass Flash, Fastboot reconnect/reboot, Xiaomi sign-in, ZIP select/clear and terminal rotation/input retain physical PASS evidence on the tested rigs.

No old TestBook campaign is part of the current release process. Tester instructions are handled in chat; the repository keeps only current release docs plus the authoritative hardware evidence ledger.

## CI and release gates

The normal main workflow must pass repository/source guards, native regressions, JVM tests, lint, Debug APK, Android API 26/34 instrumentation and APK-only tester-kit verification.

The protected release workflow additionally:
1. accepts only the version-matching tag or a main-only release request;
2. proves the release commit is current/approved main history;
3. uses GitHub Actions pinned by commit SHA;
4. verifies the permanent release certificate before build;
5. builds signed Release APK;
6. verifies the APK certificate again;
7. runs filesystem/APK checks and publishes SHA-256/build metadata;
8. creates the GitHub Release as prerelease for alpha/beta/rc versions.

## Known device note

POCO F7/onyx can intermittently fail inbound USB transfers as Android USB host. Legacy NekoFlash recorded the same class of behavior. This is not treated as a generic Pull failure because the same target/files pass on X3 Pro host. Power/VBUS sensitivity is a hypothesis only; no voltage/current measurement was captured.

## Historical detail

Chronological experiments remain in Git history and the physical evidence ledgers:
- [EN ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md)
- [RU ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md)
- [Changelog](../CHANGELOG.md)

Do not reopen old candidate instructions as new test assignments unless code or hardware scope actually changes.
