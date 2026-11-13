# NekoFlash 2.0 — current status

**Updated:** 14 November 2026

## Release in progress

- Prepared tag: `v6.0.0-alpha12`, versionCode `235`, source commit `382af3250a1bfd6b2b2b13d878f4e27f3ec43e81`.
- Includes merged PRs #97 (audit/documentation), #98 (Settings community links) and #99 (vector brand icons).
- Exact-commit [main CI #756](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43525078852): SUCCESS. 409 JVM tests passed with no failures/errors/skips, Android Lint reported no findings, and API 26/34 each passed 29 installed-app tests plus independent ext4/F2FS SHA-256/fsck checks. Source/security checks and verified tester-APK packaging also passed.
- [Release workflow](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43525078829): tag provenance PASS; branch cleanup PASS; signing/publication WAITING for the existing `nekoflash-release` environment approval by `Ncorror`.
- Publication and the new APK SHA-256 are not confirmed until the release workflow succeeds. The last published signed release remains listed below.
- Deleted merged branches: `ui/settings-community-links` and `ui/settings-brand-icons`. Only `main` remains; open Issues and PRs: 0. Default/protected/security branches, open PR refs and branches changed after their merged PR are retained by the cleanup safeguards.
- Next action: open the release workflow, choose **Review deployments → nekoflash-release → Approve and deploy**. The workflow then builds the signed APK, verifies the permanent certificate and publishes the APK, SHA-256 and required filesystem corresponding source. After SUCCESS, record the new APK checksum here and update public download/version labels.

## Signed release

- Release: [v6.0.0-alpha11](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/v6.0.0-alpha11)
- versionCode: `234`
- applicationId: `ru.forum.adbfastboottool`
- Release commit: `e305e12d39539ae699b861e1e002695385e14494`
- APK SHA-256: `dfd7648d1b786b1072c0eec850d190fa472059c79b5373b26a67f1392a5c0135`
- Signing certificate SHA-256: `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`
- Release state: published, non-draft, normal/latest release.

Public custom assets are deliberately limited to the APK, its SHA-256 file and the corresponding-source archive for bundled filesystem tools.

## Main after release

`main` is the development source of truth and may contain post-release maintenance that is not present in the already-signed alpha11 tag. Debug/test artifacts from `main` are not substitutes for the signed Release APK.

The post-release audit and Settings PRs #97–#99 are merged. Release preparation commit `382af3250a1bfd6b2b2b13d878f4e27f3ec43e81` passed main CI #756 in full. Both merged Settings branches were removed by the release workflow; only `main` remains.

## Hardware disposition

| Area | Result |
| --- | --- |
| vayu `fastboot format userdata` | FIELD PASS; Android subsequently booted |
| vayu `fastboot -w` including metadata/cache | FIELD PASS; Android setup screen reached |
| Fastboot handshake/reboot/Mass Flash on tested rigs | FIELD PASS |
| Recovery/Sideload transport and accepted status handling | FIELD PASS within documented verification boundary |
| X3 Pro host → X3 Pro target 128 MiB Pull/Push and SHA-256 | FIELD PASS |
| POCO F7/onyx host long inbound ADB | Intermittent host-specific USB receive failure; checkpoint/resume robustness implemented |
| Xiaomi sign-in/session workflow | FIELD PASS for login/session acquisition; unlock itself was not executed in the cited sign-in test |
| Terminal rotation/input/header | FIELD PASS for the reproduced regression scenario |

Detailed history: [HARDWARE_VALIDATION.md](HARDWARE_VALIDATION.md).

## Known limitations

- Hardware qualification applies only to tested device/firmware combinations.
- POCO F7/onyx can intermittently fail long inbound USB transfers as host. Power/VBUS sensitivity is plausible but not proven because voltage/current were not instrumented.
- Raw diagnostics are intentionally complete and may contain sensitive identifiers/paths.
- Xiaomi WebView authentication cookies persist until explicit Logout; this is intentional session behavior and must be considered on shared host phones.
- GitHub repository rulesets were empty when read during this audit. The protected release environment is proven by the real approval-gated release, but main branch protection could not be read through the connected GitHub App. See [REPOSITORY_SECURITY.md](REPOSITORY_SECURITY.md).

## Next development rule

Do not reopen old REV/TestBook/audit checklists as current work. New work starts from current `main`, current issues and reproducible evidence.
