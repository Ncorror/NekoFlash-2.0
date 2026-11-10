# NekoFlash Insiders — device testing and evidence / аппаратное тестирование

**Current release-candidate source:** [`main=0faca68e302b`](https://github.com/Ncorror/NekoFlash-2.0/commit/0faca68e302b8007f340926025f7672cb9e6b3cb), 10 Nov 2026. [Post-merge CI #647](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42736593646) **SUCCESS**. This remains DEV/release-candidate material until a permanent-key signed production APK is built on the final SHA.

**RU.** Уже выполненные аппаратные проверки не повторяем по старым карточкам. Реестр: [RU](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md) / [EN](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). Физический PASS исправленного вывода/заголовка терминала после поворота 7 ноября остаётся принятым. Mass Flash, Recovery, ZLP, 512 файлов и 16 МиБ также сохраняют свои исходные результаты.

**Sideload:** PR [#74](https://github.com/Ncorror/NekoFlash-2.0/pull/74) уже в `main`: **Choose ZIP** только выбирает документ, **Start Sideload** отдельно запускает подтверждённую передачу. Старые инструкции, где выбор файла сразу отправлял ZIP, больше не описывают текущий `main`.

**Post-release scope:** #20, #46, #47, #48, #64 and #72 remain open by project decision; PRs #67, #68 and #73 are isolated post-release branches. They are not a reason to rerun the completed destructive campaign before alpha11.

## Correct kit / правильный комплект

1. Choose a **successful current `main` workflow**: [GitHub Actions downloads](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess). The `NekoFlash-Insiders-Tester-Kit` artifact contains **exactly two files**: Debug APK and `testbook.html`. Choose the kit's precise CI run, record the run ID and source SHA.
2. APK and HTML must originate from the **same downloaded ZIP**. The packaged HTML embeds the matching APK SHA-256, commit and CI run ID. Save old DEV logs and TestBook JSON progress before replacing a DEV installation. No work data or logs should be deleted for the sake of testing.
3. Chrome `content://` fallback issue [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49) is closed in the current source, but a browser Download click still is not proof that Android saved a file. Verify the actual JSON/ZIP on storage; the APK's own SAF diagnostic export remains a separate mechanism. Keep private evidence in a trusted chat.
4. The 43-case TestBook (six quick tasks, six gated LAB cases) was **prepared and CI-checked**, not physically executed in full. Its interface and export logic were not changed by PR #62. Historical checkmarks are not automatic new-pass evidence. On a locked TARGET never use destructive LAB entries.

## Pinned historical REV45 / архивная контрольная сборка

[Download **historical REV45 ZIP**](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip) · [REV45 artifact audit](AUDIT-2026-11-02.md) · [old RU guide](FIELD-GUIDE-RU.md) · [old EN guide](FIELD-GUIDE-EN.md).

**The pinned REV45 archive and the standalone `docs/insiders/testbook.html` in the repository do not necessarily identify the newest APK.** Do not mix reference `sourceCommit` / `apkSha256` from old HTML with a newly downloaded APK. Tag `insiders-rev49` in `campaign-tag.txt` is a separate historical/publishing mechanism; this documentation audit does not publish a new prerelease.

[Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy) · [hardware qualification](../HARDWARE_VALIDATION.md) · [open issues](https://github.com/Ncorror/NekoFlash-2.0/issues?q=is%3Aissue+is%3Aopen).
