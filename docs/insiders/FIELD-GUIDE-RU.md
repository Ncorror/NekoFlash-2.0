# NekoFlash Insiders — инструкции по тестированию

Тесты, готовые команды и отчёты теперь ведём **непосредственно в чате**. Актуальный [CI main](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) выдаёт комплект только с APK.

Сейчас проверяем новые элементы: очистку выбранного ZIP, поле версии Android после ADB SYSTEM и индикатор ожидания Mi-аккаунта. Укажи HOST/TARGET, build ID, результат, скриншот и полный ZIP диагностики из NekoFlash. Логи не удаляем и не обрезаем.

Ранее пройденные аппаратные тесты: [реестр](../testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72) и [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64) оставляем для отдельного исследования; никакого повторного wipe/flash для галочек.
