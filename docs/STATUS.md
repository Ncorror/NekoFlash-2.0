# NekoFlash 2.0 — current status

**Updated:** 14 November 2026

## Signed release

- Release: [v6.0.0-alpha12](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/v6.0.0-alpha12)
- versionCode: `235`
- applicationId: `ru.forum.adbfastboottool`
- Release commit: `382af3250a1bfd6b2b2b13d878f4e27f3ec43e81`
- APK SHA-256: `f5013cb895d11f7c42272075eb384e0b0bbc9d1fa3d537321504fbd01499e53b`
- Signing certificate SHA-256: `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`
- [Release workflow](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43525078829): SUCCESS; published, non-draft, normal/latest release.
- Exact-commit [main CI #756](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43525078852): SUCCESS. 409 JVM tests, 29 installed-app tests on each of API 26/34, independent ext4/F2FS SHA-256/fsck verification and Android Lint without findings.

Public assets are the signed APK, its SHA-256 file and the required filesystem corresponding-source archive. Alpha11 remains available as a historical signed release.

## Main and public website

PRs #97–#99 (audit, Settings community links and vector brand icons) are merged. Both merged Settings branches were deleted by the release workflow; only `main` remains. The following documentation/site changes do not modify the signed release APK.

- Public website: [English](https://nekoflash.ncorror.chatgpt.site/) · [Русский](https://nekoflash.ncorror.chatgpt.site/ru/).
- Website source mirror: this repository's `docs/` directory. Hosting source: `c21c024478ffd66b6cf37794df79d3fd68be8eeb`, Site version 2 (published successfully on 14 November).
- The redesigned home contains 136 Russian words (previously 612), a direct signed APK download, a four-screen gallery and a narrow-screen layout. Detailed setup, compatibility, evidence and the Xiaomi retest request are on localized help pages. SHA-256 is available on demand.
- RU/EN canonical/hreflang metadata, JSON-LD and robots.txt are present; the sitemap covers 12 URLs. Google Search Console ownership verification and sitemap submission are the next account-dependent step. Search-engine inclusion is not claimed or verified.
- [4PDA BBCode draft](4PDA_POST_RU.bbcode) requests volunteer verification of actual Xiaomi bootloader unlocking. It is a prepared draft, not a claim that a forum post was submitted.
- NekoFlash previously unlocked POCO X3 Pro, POCO F5 and POCO X7 Pro (maintainer-confirmed history). Mi Account sign-in/Fastboot detection are verified in the current campaign; the rewritten unlocking code needs a repeated end-to-end test on a locked device.

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
