# NekoFlash Insiders — device testing and evidence / аппаратное тестирование

**Current Insiders source/CI checkpoint — 11 Nov 2026:** `6.0.0-alpha11` / versionCode 234, [`main` faebd3a](https://github.com/Ncorror/NekoFlash-2.0/commit/faebd3a338ecd695c9ec4a879fa6a8c92db79fba), [Actions #42890625837 SUCCESS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42890625837). Download the two-file [baseline APK + matching TestBook artifact](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42890625837/artifacts/13799940338), or a newer successful `main` kit; always keep APK and HTML from the **same** run. This is a debug field-test package; permanent-key signed production alpha11 has **not** been verified. [Large Pull EPROTO #72](https://github.com/Ncorror/NekoFlash-2.0/issues/72) remains open and must be reported as host/target-specific evidence, not a universal failure. Experimental [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83) is excluded from `main`. Never claim a missing large-Pull hardware PASS from CI alone.

**RU.** Уже выполненные аппаратные проверки не повторяем по старым карточкам. Реестр: [RU](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md) / [EN](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). Физический PASS исправленного вывода/заголовка терминала после поворота 7 ноября остаётся принятым. Mass Flash, Recovery, ZLP, 512 файлов и 16 МиБ также сохраняют свои исходные результаты.

**Sideload:** PR [#74](https://github.com/Ncorror/NekoFlash-2.0/pull/74) уже в `main`: **Choose ZIP** только выбирает документ, **Start Sideload** отдельно запускает подтверждённую передачу. Старые инструкции, где выбор файла сразу отправлял ZIP, больше не описывают текущий `main`.

**Post-release scope:** #20, #46, #47, #48, #64 and #72 remain open by project decision. The fail-closed #67 regression and conservative Recovery-transition #68 logic are included in the alpha11 candidate via PR #76; experimental ADB Pull PR #73 remains excluded. None of this is a reason to rerun the completed destructive campaign.

## Correct kit / правильный комплект

1. Choose a **successful current `main` workflow**: [GitHub Actions downloads](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess). The `NekoFlash-Insiders-Tester-Kit` artifact contains **exactly two files**: Debug APK and `testbook.html`. Choose the kit's precise CI run, record the run ID and source SHA.
2. APK and HTML must originate from the **same downloaded ZIP**. The packaged HTML embeds the matching APK SHA-256, commit and CI run ID. Save old DEV logs and TestBook JSON progress before replacing a DEV installation. No work data or logs should be deleted for the sake of testing.
3. Chrome `content://` fallback issue [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49) is closed in the current source, but a browser Download click still is not proof that Android saved a file. Verify the actual JSON/ZIP on storage; the APK's own SAF diagnostic export remains a separate mechanism. Keep private evidence in a trusted chat.
4. The 43-case TestBook (six quick tasks, six gated LAB cases) was **prepared and CI-checked**, not physically executed in full. Its interface and export logic were not changed by PR #62. Historical checkmarks are not automatic new-pass evidence. On a locked TARGET never use destructive LAB entries.

## Pinned historical REV45 / архивная контрольная сборка

[Download **historical REV45 ZIP**](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip) · [REV45 artifact audit](AUDIT-2026-11-02.md) · [old RU guide](FIELD-GUIDE-RU.md) · [old EN guide](FIELD-GUIDE-EN.md).

**The pinned REV45 archive and the standalone `docs/insiders/testbook.html` in the repository do not necessarily identify the newest APK.** Do not mix reference `sourceCommit` / `apkSha256` from old HTML with a newly downloaded APK. Tag `insiders-rev49` in `campaign-tag.txt` is a separate historical/publishing mechanism; this documentation audit does not publish a new prerelease.

[Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy) · [hardware qualification](../HARDWARE_VALIDATION.md) · [open issues](https://github.com/Ncorror/NekoFlash-2.0/issues?q=is%3Aissue+is%3Aopen).
