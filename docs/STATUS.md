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
- Website source mirror: this repository's `docs/` directory. Hosting source: `ecd9310e03af5994821cbda306a4c7aaaa2dda9d`, Site version 3 (published successfully on 14 November; includes the Google verification file).
- The redesigned home contains 136 Russian words (previously 612), a direct signed APK download, a four-screen gallery and a narrow-screen layout. Detailed setup, compatibility, evidence and the Xiaomi retest request are on localized help pages. SHA-256 is available on demand.
- RU/EN canonical/hreflang metadata, JSON-LD and robots.txt are present; the sitemap covers 12 URLs. Google Search Console URL-prefix ownership was confirmed through the HTML file on 14 November. Google processed `/sitemap.xml` successfully and reported 12 discovered pages. Actual search-engine inclusion remains unverified.
- URL Inspection reported both home URLs as unknown to the index at setup time. A manual indexing request for `/ru/` was rejected with "Quota exceeded" / daily quota on 14 November; it was not accepted. No repeat or English manual request was sent after that rejection. The successfully processed sitemap remains submitted for all 12 URLs.
- **SEO guide mirror staged on 14 November:** six new RU/EN pages were added under `docs/guides/` and `docs/ru/guides/` for flashing Android without PC, ADB without PC and Xiaomi unlock without PC, with updated guide indexes, home navigation and an **18-URL sitemap mirror**; README public links deliberately stay on existing help sections until publication. These repository edits **do not deploy the live Site**. The deployed site's 12-URL sitemap and Search Console submission remain the last confirmed live state; regenerate the Site project's actual source (`site-data.json`/`scripts/render_site.py`), publish it, then verify the new URLs before resubmitting its updated sitemap.
- **English gallery asset audit:** the four `docs/assets/alpha12-*.png` images on the English homepage contain Russian UI, despite English captions. Older English `docs/screenshots/*.png` show a different UI and must not be passed off as alpha12. The Android manifest declares English and Russian per-app locales. Capture four actual alpha12 screens in English (Quick Flash, Sideload, Terminal, Xiaomi Unlock), replace the English site's four gallery assets in the Site source, keep Russian screenshots for `/ru/`, update image alt text, publish and inspect both locales. Until then the staged mirror labels the English gallery as Russian-UI screenshots; no replacement images are claimed or published.
- **Mobile and content discovery:** `docs/` site mirror now has compact task-oriented links visible on narrow screens and descriptive EN/RU gallery alt text. They are staged changes only; the Site source must be updated and redeployed before users and Google see them.
- **SEO checkpoint:** `ce338e910449aa46d02002aaee13413ca9a93df0` adds `og:image`, large social preview cards and breadcrumb schema to the six new guide pages (RU/EN). Verified on `main`: six pages all contain those tags and TechArticle metadata; the 18-page staged sitemap has no unresolved internal links or canonical mismatches in the mirror. This remains source-only until the Site project is updated and republished. The existing OG artwork itself still features a legacy English UI image; replace it with a current alpha12 English capture or a screenshot-free branded graphic at the next Site publication.
- Keep `googlee972052e7ba1ecb2.html` at the public root when regenerating or deploying the site; removing it can revoke Search Console verification.
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
