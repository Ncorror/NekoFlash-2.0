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
- Initial live Site version 3 used a compact Russian landing page and four-screen gallery. The current GitHub `docs/` mirror now includes six English development-build screenshots, localized original Russian captures, the approved promotional banner, and mobile guide links. These updates still require separate Site publication.
- RU/EN canonical/hreflang metadata, JSON-LD and robots.txt are present; the sitemap covers 12 URLs. Google Search Console URL-prefix ownership was confirmed through the HTML file on 14 November. Google processed `/sitemap.xml` successfully and reported 12 discovered pages. Actual search-engine inclusion remains unverified.
- URL Inspection reported both home URLs as unknown to the index at setup time. A manual indexing request for `/ru/` was rejected with "Quota exceeded" / daily quota on 14 November; it was not accepted. No repeat or English manual request was sent after that rejection. The successfully processed sitemap remains submitted for all 12 URLs.
- **SEO guide mirror staged on 14 November:** six new RU/EN pages were added under `docs/guides/` and `docs/ru/guides/` for flashing Android without PC, ADB without PC and Xiaomi unlock without PC, with updated guide indexes, home navigation and an **18-URL sitemap mirror**; README public links deliberately stay on existing help sections until publication. These repository edits **do not deploy the live Site**. The deployed site's 12-URL sitemap and Search Console submission remain the last confirmed live state; regenerate the Site project's actual source (`site-data.json`/`scripts/render_site.py`), publish it, then verify the new URLs before resubmitting its updated sitemap.
- **English UI screenshots (14 November):** six real `ru.forum.adbfastboottool.dev` English screenshots (Welcome, Home, Terminal, Quick Flash, Sideload, Mi Unlock) were uploaded. `docs/assets/en-{welcome,home,terminal,flash,sideload,unlock}.webp` are **committed to GitHub `main`** and linked from the mirrored English home gallery. These are development-build screenshots, not a claim of alpha12 production screenshots. Live Site publication remains unverified.
- **Gallery disposition:** six real English development-build screens are staged in the GitHub site mirror. Russian `alpha12-*.png` captures remain available for `/ru/`; older English `docs/screenshots/*.png` reflect historical UI and are not presented as current. Do not claim the public Site gallery is synchronized until its separate Site project is published and verified.
- **Mobile and content discovery:** `docs/` site mirror now has compact task-oriented links visible on narrow screens and descriptive EN/RU gallery alt text. They are staged changes only; the Site source must be updated and redeployed before users and Google see them.
- **SEO checkpoint:** `ce338e910449aa46d02002aaee13413ca9a93df0` adds `og:image`, large social preview cards and breadcrumb schema to the six new guide pages (RU/EN). Verified on `main`: six pages all contain those tags and TechArticle metadata; the 18-page staged sitemap has no unresolved internal links or canonical mismatches in the mirror. This remains source-only until the Site project is updated and republished. The existing OG artwork itself still features a legacy English UI image; replace it with a current alpha12 English capture or a screenshot-free branded graphic at the next Site publication.
- **Approved NekoFlash 2.0 banner:** the official artwork shows the girl, connected phone cable, green Android robot, cat, real English Home screenshot, and `NekoFlash 2.0` identity. The site mirror links a compact WebP banner in both RU/EN home pages and READMEs. The 1200x630 PNG Open Graph preview is used consistently across all 18 localized site pages. **This is GitHub mirror only, not a deployment of the separately hosted Site project.** Publish the Site source and then verify its metadata; no Search Console resubmission is claimed.
- **README polish (14 November):** removed the redundant 88px launcher cat icon above `# NekoFlash 2.0` from both `README.md` and `README_ru.md`; retained the approved banner and its link. Verified `main` commits `471a0de6cc94c513d69740136153b11e1cb3417f` (EN) and `b0be82ece49bff7e51554cebd20ea77a69a41c15` (RU). No app source or release APK changed.
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
