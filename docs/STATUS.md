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

PRs #97–#99 (audit, Settings community links and vector brand icons) are merged. Only `main` remains. The website/documentation changes below do not modify the signed release APK.

- Public website: [English](https://nekoflash.ncorror.chatgpt.site/) · [Русский](https://nekoflash.ncorror.chatgpt.site/ru/).
- **Published 14 November:** the existing public Site is now **version 6**, hosting source `307639a924a50751e9cbbbaf71493f6a73e453fb`. The approved banner is the first content below the header. The repeated large promotional headline, eyebrow and introductory paragraph below it have been replaced by a compact download block with a functional heading, APK/GitHub actions, compatibility and release/checksum information. EN/RU narrow layouts and both published home pages were checked. The existing project, public address and audience were preserved; the native publication returned `succeeded`.
- The GitHub `docs/` mirror and live Site now include **18 localized URLs**: both home/help pages, guide indexes and six workflows per language (Android flashing without a PC, ADB without a PC, Fastboot from Android, Recovery/TWRP, ADB Sideload and Xiaomi unlock without a PC).
- **Live browser validation:** all 18 sitemap pages render with matching canonical URLs, headings and the approved Open Graph image. The public sitemap lists 18 unique URLs. The Google verification URL returns the exact expected verification text; `googlee972052e7ba1ecb2.html` remains in the deployed root. Direct HTTP crawling from the executor returned 403, so status/header crawling is not claimed.
- **Gallery and branding:** the approved promotional WebP banner is published on both home pages. Six real English `.dev` screenshots are explicitly identified as development-build captures; four original Russian alpha12 screenshots are retained. English tab selection and language switching were checked live. The initial Home tab now displays the Home image and matching alt text. The existing `site-data.json` / `scripts/render_site.py` workflow preserves these changes when regenerated.
- **Responsive and SEO checks:** narrow EN/RU layouts, the banner, gallery navigation and guide links were checked in the browser. The 1200×630 public `/assets/og-image.png` loads. Local internal-link/fragment checks, sitemap uniqueness, canonical/image metadata checks and JavaScript syntax validation passed. Both READMEs retain the approved banner without the redundant icon above the heading; links now lead directly to the published new guides.
- **README polish (14 November):** both localized READMEs now provide six repository-native SVG action buttons (direct signed APK, SHA-256 file, releases, website, Telegram and guides), three-step connection instructions, a capabilities table, real localized screenshots and six workflow guides. Release-integrity and validation details are collapsible. Button download targets were verified against the current alpha12 release assets; local SVG/image/relative-link checks passed. The site banner loads eagerly as the first main section, and the narrow EN/RU layout keeps the download action within the first screen.
- **Google Search Console:** URL-prefix ownership was confirmed on 14 November. After version 4 publication, `sitemap.xml` was resubmitted and Google displayed **“Файл Sitemap отправлен”**. The report still showed **12 discovered pages** from the previous processing pass; processing all 18 URLs and actual search-engine inclusion are **not yet confirmed**. Performance/indexing overview reports are still processing data.
- The earlier manual indexing request for `/ru/` was rejected with “Quota exceeded” on 14 November. No new manual indexing request was sent during this publication.
- **Next step:** check Search Console's discovered-page and indexing reports after Google processes the updated sitemap. Prepared 4PDA/Telegram promotion remains a separate action; no forum post or group message was sent during publication.
- [4PDA BBCode draft](4PDA_POST_RU.bbcode) requests volunteer verification of actual Xiaomi bootloader unlocking. NekoFlash previously unlocked POCO X3 Pro, POCO F5 and POCO X7 Pro (maintainer-confirmed history). Mi Account sign-in/Fastboot detection are verified in the current campaign; the rewritten unlocking code still needs a repeated end-to-end test on a locked device.

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
