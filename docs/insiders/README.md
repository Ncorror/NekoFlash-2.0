# NekoFlash Insiders — current field process

Current accepted main baseline: `7273b014` / `6.0.0-alpha11`, [CI #698 SUCCESS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43068208355).

**RU:** TestBook отменён. Новый [Insiders kit из CI](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) содержит **ровно одну APK**. Тесты, команды и PASS/FAIL ведутся в чате, полный ZIP диагностики выгружается из NekoFlash. Уже проведённые аппаратные проверки остаются в [реестре RU](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md); не повторять их автоматически. Основные новые проверки: «Очистить ZIP», Android release, индикатор Xiaomi.

**EN:** The HTML tracker is retired. Use the single debug APK from a successful exact-source CI run. Report command outcomes, screenshots, build ID and complete NekoFlash diagnostics in the trusted chat. Preserve the [hardware evidence ledger](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). No repeated destructive tests.

**Historical:** [insiders-rev50](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/insiders-rev50) is an older published debug build; its existing asset remains unchanged.

**Separate hardware investigations:** [#72 USB EPROTO](https://github.com/Ncorror/NekoFlash-2.0/issues/72), [#64 vayu Fastboot wipe](https://github.com/Ncorror/NekoFlash-2.0/issues/64); unmerged [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83).

[Telegram Insiders](https://t.me/+NR_FiUmvXlViOTUy)
