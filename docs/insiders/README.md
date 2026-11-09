# NekoFlash Insiders — device testing and evidence / аппаратное тестирование

**Current development source:** [`main=6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e) (8 Nov 2026). [Post-merge GitHub CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42517047929) **SUCCESS**. This is DEV, not a new signed production release.

**RU.** Прежде чем начинать новые испытания, посмотри [реестр уже выполненных аппаратных тестов](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md) и [сверку всех 43 пунктов TestBook](../testing/TESTBOOK-43-EVIDENCE-MAP-2026-11-08-RU.md). В нём записаны действительные прошивки, ADB, ZIP и все открытые результаты; не нужно повторять по кругу 512 файлов, 16 МиБ, Fastboot recovery **или уже пройденный аппаратный тест исправленного терминала после поворота 7 ноября** ([подтверждённый PASS](https://github.com/Ncorror/NekoFlash-2.0/pull/45#issuecomment-6707179253)) только из-за старых карточек TestBook. Новые задачи согласовываем от реестра. [Документальный аудит](../testing/DOCUMENTATION-AUDIT-2026-11-08-RU.md) объясняет старые расхождения.

**EN.** Check the [real hardware evidence ledger](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md) before assigning new tasks. Previously completed 512-file transfers, 16 MiB, real Recovery and `vendor_boot` flashing remain recorded by build and device. A checklist is **not** proof that the work has or has not been done. The next focused physical test plan is to be agreed from the evidence gaps, not blindly from 43 cards.

## 9 November 2026 update / обновление

**RU:** На разблокированном `vayu` реальные **Mass Flash `boot/dtbo/recovery` 3/3 PASS**, `fastboot -w` и Android после `format userdata` — **FIELD FAIL**; исходный Lineage ZIP был с неверным SHA-256; Sideload OrangeFox из Lineage Recovery привёл к появлению другого Recovery, но installer exit-код не получен. [Полная таблица](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). **Не повторять destructive операции ради галочек TestBook.** Для теста **исправления выбора ZIP** смотри [PR #71](https://github.com/Ncorror/NekoFlash-2.0/pull/71), который уже прошёл [CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42533760615), но **пока не слит в `main` и не испытан на телефоне**. Старый `main` запускает Sideload прямо после выбора файла — не считай его «безопасным выбором».

**EN:** Actual unlocked-vayu Mass Flash `boot/dtbo/recovery` **FIELD PASS 3/3**, whereas `fastboot -w` partially failed and Android did not boot after `format userdata`. The original Lineage ZIP was hash-mismatched. OrangeFox ZIP sent from Lineage Recovery was followed by a different Recovery environment, without a captured installer exit code. [Detailed evidence](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). **Do not rerun destructive operations just for TestBook checkboxes.** New explicit Choose/Start Sideload exists in green [PR #71](https://github.com/Ncorror/NekoFlash-2.0/pull/71) ([CI #42533760615](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42533760615)), **not in `main` yet**. Main's current picker automatically starts sending a ZIP.

## Correct kit / правильный комплект

1. Choose a **successful current `main` workflow**: [GitHub Actions downloads](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess). The `NekoFlash-Insiders-Tester-Kit` artifact contains **exactly two files**: Debug APK and `testbook.html`. Choose the kit's precise CI run, record the run ID and source SHA.
2. APK and HTML must originate from the **same downloaded ZIP**. The packaged HTML embeds the matching APK SHA-256, commit and CI run ID. Save old DEV logs and TestBook JSON progress before replacing a DEV installation. No work data or logs should be deleted for the sake of testing.
3. If Chrome opens via `content://`, **Copy/ZIP/JSON save is not yet physically accepted** ([issue #49](https://github.com/Ncorror/NekoFlash-2.0/issues/49)); do not report a button click as a saved file. The APK's own SAF diagnostic export is a separate mechanism. Keep private evidence in a trusted chat.
4. The 43-case TestBook (six quick tasks, six gated LAB cases) was **prepared and CI-checked**, not physically executed in full. Its interface and export logic were not changed by PR #62. Historical checkmarks are not automatic new-pass evidence. On a locked TARGET never use destructive LAB entries.

## Pinned historical REV45 / архивная контрольная сборка

[Download **historical REV45 ZIP**](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip) · [REV45 artifact audit](AUDIT-2026-11-02.md) · [old RU guide](FIELD-GUIDE-RU.md) · [old EN guide](FIELD-GUIDE-EN.md).

**The pinned REV45 archive and the standalone `docs/insiders/testbook.html` in the repository do not necessarily identify the newest APK.** Do not mix reference `sourceCommit` / `apkSha256` from old HTML with a newly downloaded APK. Tag `insiders-rev49` in `campaign-tag.txt` is a separate historical/publishing mechanism; this documentation audit does not publish a new prerelease.

[Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy) · [hardware qualification](../HARDWARE_VALIDATION.md) · [open issues](https://github.com/Ncorror/NekoFlash-2.0/issues?q=is%3Aissue+is%3Aopen).
