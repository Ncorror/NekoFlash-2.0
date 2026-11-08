# NekoFlash 2.0 · интеграционная точка и фактическая квалификация, 8 ноября 2026

## Текущая база: `main=6a8accd8e33e` после PR #62

**Исходники:** [`6a8accd8e33eef946efa1f18a829b0cc2b797e6e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e). Последовательные изменения: REV49 (#45), REV51 (#51), REV52 (#54), REV53 (#55), REV54 (#58), REV55 (#59), документация #60, проверка состава диагностических source #61, задания TestBook #62. **Стабильный подписанный релиз на этот SHA не объявлялся.**

**Автоматические проверки:** PR #62 [CI #42515445165](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42515445165) **PASS**, затем отдельный [post-merge main CI #42517047929](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42517047929) **SUCCESS** по тому же источнику интеграции: build, API26, API34, упакованный двухфайловый Insiders kit и pinning успешны. Число 336 JVM/28+28 относится к отдельному точному [REV55 CI #501](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42426015449), а не автоматически к каждому будущему SHA.

**Аппаратные испытания уже проводились** и не должны вновь назначаться как «первые»:
- **H1** POCO F7 Android16 → разблокированный POCO X7 Pro rodin: ранее на alpha10 реальная запись `vendor_boot_a` и `vendor_boot_b` по 64 МиБ, Fastboot и final boot PASS.
- **H2** POCO F7 Android16 → **разблокированный** POCO X3 Pro vayu Android14: на alpha10.1 Recovery flash 128 МиБ, три Recovery-verified Sideload ZIP, Fastbootd и final boot PASS; отдельная **REV48** запись Recovery 128 МиБ и переход в Fastbootd PASS, но **REV48 OrangeFox-R11.3 ZIP остался VERIFY_PENDING**, не установленный PASS.
- **H2, REV51–53:** ZIP SHA 55/55, 103/103, 35/35, 46/46 для **сохранённых данных**; в REV52 короткий shell падал, затем в REV53 10/10 и 2/2 физически PASS. Старые пропущенные заголовки REV48 не восстановлены.
- **H3** POCO F7 Android16 → **другой заблокированный** POCO X3 Pro vayu Android12: исходный ZLP баг 223/512, 1016-байтный файл FAIL 3/3; после REV54 1023/1024/1025, 512-file push+pull SHA и 16 МиБ round-trip **FIELD PASS**. [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) закрыта только для этой связки. **На H3 разделы не прошивались.**

**Первичные архивы тестировщика:** после обнаружения пропущенных материалов локально проверены CRC десяти ранее отправленных ZIP, а для семи диагностических ZIP — все записи `EXPORT-MANIFEST.txt` по SHA-256/размеру; REV51/52 имеют `INCOMPLETE_OR_UNVERIFIED`, поздние REV53/54 — `CONSISTENT_SNAPSHOT`. Данные и границы см. в приложении к [реестру](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). Это не доказательство восстановления отсутствовавших исторических событий. Старые файлы сохранены приватно.

**Полный [реестр свидетельств](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md)** / [EN](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md), [сверка 43 карточек TestBook](testing/TESTBOOK-43-EVIDENCE-MAP-2026-11-08-RU.md) и [акт документального аудита](testing/DOCUMENTATION-AUDIT-2026-11-08-RU.md) — приоритетные источники. Исторические разделы ниже сохраняются как датированные снимки, а не текущий план.

### Неподтверждённые и открытые пункты

- [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49): REV48 TestBook Chrome `content://` copy/ZIP/JSON **FIELD FAIL**; новый fallback проверен кодом/CI, но реального физического PASS нет. Экспорт raw ZIP из APK — отдельный успешно наблюдавшийся механизм.
- [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50): целостность байтов ZIP проверялась физически, но исторический начальный `log`/`trace` REV48 отсутствовал; `--check-source-inventory` PR #61 подтверждён CI, не новая аппаратная приемка всех источников.
- [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47): возвращавшаяся после force-stop плашка `VERIFY_PENDING`; код исправлялся, физический регрессионный итог на новой APK не установлен. [#46](https://github.com/Ncorror/NekoFlash-2.0/issues/46) — **дизайн Operation Center отложен** по решению автора.
- [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48): intermittent DocumentsUI root, без доказанной причины; [#20](https://github.com/Ncorror/NekoFlash-2.0/issues/20): **основной сдвиг вывода/заголовка после поворота уже FIELD PASS 7 ноября** ([PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45)), отдельно остаются вопросы задержки анимации и редкого маленького viewport/IME; [#19](https://github.com/Ncorror/NekoFlash-2.0/issues/19), [#36](https://github.com/Ncorror/NekoFlash-2.0/issues/36): большие буферы/память и общий стресс, не закрытые одним 512-файловым прогоном; [#16](https://github.com/Ncorror/NekoFlash-2.0/issues/16) — более широкая нестабильность CI-эмулятора.
- **TestBook:** 43 сценария и LAB-01…LAB-06 — **список заданий**, не 43 новых аппаратных результата. Его дизайн, данные/прогресс/экспорт на PR #62 сохранены; исторические галочки не засчитывают destructive. **Следующие тесты составляются по дельте от реестра здесь в чате**, а не автоматически по всем карточкам. Существующие TestBook/отчёты не удалять.
- **Получение APK:** после зелёного `main` CI бери [Insiders tester kit](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess), состоящий только из APK и соответствующего testbook.html (run ID, commit и SHA совпадают). Опубликованный REV45 pinned kit остаётся **архивным**; сам исходный HTML в `main` может включать старую ссылку на его контрольную сборку.

---

## Исторические снимки (сохранены без подмены версий)

Ниже — прежние отчёты REV27/43/45/46–55. Любое утверждение «текущий», «main», «ещё не прошли» в них относится **к дате соответствующего исторического среза**, не к текущему `main`.

## Архивный снимок до интеграции REV49 — 7 ноября 2026

На тот момент изменения были в `fix/rev46-simple-tests-logs`, [PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45), а публичный `main` ещё находился на `43537822e047`. Ниже сохранена история развития REV46–49, **не актуальное состояние веток сегодня**.

- REV46: TestBook по умолчанию показывает шесть заданий; прежние 43 проверки и отметки сохранены. Экспорт исходных диагностических журналов через системный диалог Android работает без обязательного доступа ко всему хранилищу. После подготовки HTML-отчёта есть отдельная кнопка скачивания ZIP.
- REV47: старое завершённое табло не открывается заново при восстановлении из журнала. Проверяется идентификатор сессии, поскольку Android может передать сохранённый Bundle новому процессу. Новый `VERIFY_PENDING` скрывается через 20 секунд; результат и журнал остаются без изменения.
- Добавлены три Android-проверки реального экрана: запуск со старым checkpoint, активный прогресс/поворот/автоскрытие и восстановленный Bundle с новой сессией. Они входят в матрицу API 26/34; итог проверять по CI **точного текущего коммита PR**.
- REV48: по новому поручению автора переработан операционный центр: прокручиваемые подробности, отдельное сворачивание, консоль/отчёты, краткий итог вместо завершённых «100%», восстановление окна и срока скрытия при пересоздании. Вкладка «Инструменты» временно скрыта; старый выбор вкладки переводится на «Быстрая». [Аудит REV48](source-audit/REV48-OPERATION-CENTER-2026-11-07-RU.md).

[Разбор REV47 и границы проверки](source-audit/REV47-OPERATION-RESULT-2026-11-06-RU.md). Физическая проверка новой APK остаётся отдельным шагом; исходы аппаратных операций не выводятся из эмуляторных результатов.

- REV49: физический отчёт REV48 подтвердил пустой вывод и превью в заголовке после поворота/отправки команды. Синхронизация терминала перенесена после восстановления Android view state; новая проверка отправляет команды после каждого поворота с IME. [Разбор REV49](source-audit/REV49-TERMINAL-RESTORE-2026-11-07-RU.md).

## Проверенная база REV45 · 2 ноября

**REV45 проходит тестирование на физических устройствах.** Контрольная сборка `a31dedadd4ad` прошла build и обе проверки Android в [main CI #413](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886). В отчётах 308/308 JVM, 20/20 API 26, 20/20 API 34, lint без замечаний. Для этой сборки ещё нужны результаты с реальных HOST → TARGET.

[Комплект Insiders](insiders/README.md) · [Аудит и открытые проверки](insiders/AUDIT-2026-11-02.md) · [Матрица устройств](HARDWARE_VALIDATION.md).

## История REV44–45 · срез 1 ноября

> **Аудит актуальной тестовой базы (REV45, 1 ноября 2026):** [полный чек-лист, реальные CI и устранённые предупреждения lint](AUDIT-REV45-2026-11-01-RU.md). REV44 объединена в `main` через PR #39 (`34990ea14f21`). [Успешный интеграционный CI 41342607390](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342607390) не отменяет [падение следующего CI 41342739222](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342739222): на API 26 два теста с клавиатурой. В [REV45 run 41346454626](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41346454626) проверены 308 JVM PASS, 0 Lint warnings и API26 20/20 PASS; API34 19/20 FAIL (высота терминала 46/48 при повороте). Фикс теста API34 — [PR #40](https://github.com/Ncorror/NekoFlash-2.0/pull/40); требуется полный успешный повторный CI. Официальный подписанный release не создавался.

**Архив состояния на момент REV44.** Предыдущие ревизии и их CI сохранены в исторических разделах ниже. Не переносить старые PASS на REV43.

| Объект | Подтверждённое состояние |
|---|---|
| Репозиторий | `Ncorror/NekoFlash-2.0`, не NekoFlash Pro |
| Публичная тестовая база | `main` — интеграция REV26–44 через [PR #39](https://github.com/Ncorror/NekoFlash-2.0/pull/39), **не production release** |
| Предыдущая рабочая DEV-ветка | `work/rev26-terminal-imports-20261030` (сохранена для истории) |
| Последний функциональный коммит DEV до интеграции | `bd0d1fbf0ff5ba69634df5281fa87e2ff1289bf5` (историческая точка) |
| Последняя объединённая функциональная ревизия | REV43, [PR #37](https://github.com/Ncorror/NekoFlash-2.0/pull/37), streaming `adb push -r` + Stop/USB cancellation |
| Точный исходный PR коммит | `27ab796d100387b3530bd88a6faf28e48d1c717c` |
| PR CI REV43 | [Run #41247325391](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391): **BUILD/JVM/lint/API26/API34 PASS** |
| Повторный CI объединённого DEV | [Run #41248168147](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147): BUILD PASS, API34 PASS, **API26 FAIL (2 UI timeouts)** |
| Тестовая Debug APK | [GitHub Actions artifact REV43](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391/artifacts/13060555171), пакет `ru.forum.adbfastboottool.dev`, versionCode 234, `6.0.0-alpha11-dev` |
| Тестбук | [Offline RU/EN HTML](insiders/testbook.html), [инструкция RU](testing/INSIDERS-FIELD-TEST-REV43-RU.md), [EN](testing/INSIDERS-FIELD-TEST-REV43-EN.md) |
| Физический HOST → TARGET для REV43 | **Тестовая кампания начинается, новые PASS пока не подтверждены** |
| Публикация | `main` используется как **публичная тестовая база**, релизный тег/подписанный release не создавался |

## Где скачать тестбук

[**Скачать автономный REV43 HTML прямо из main**](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html). Он поддерживает RU/EN, этапы, чекбоксы, статусы и свободные замечания, JSON-прогресс, ZIP-отчёт с исходными `log-*`, `trace-*.bin`, `session-summary-*` и фотографиями. Он не отправляет файлы на сервер и требует ручного разрешения на папку логов. Каждый успешный build CI также экспортирует тестбук как отдельный скачиваемый GitHub Actions artifact. Тестовая Telegram-группа: **[NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy)** (ссылка предоставлена автором проекта).

**Внимание:** GitHub Actions artifact APK — отдельный ZIP; не путать с архивом отчёта. SHA-256 старой REV29 APK не относится к REV43: проверь новый APK самостоятельно. Не удаляй установленную стабильную версию NekoFlash.

## Фактически выполненные изменения REV34–43

- **REV34–37:** работа над BottomSheet/IME, UTF-8 оболочкой, вводом и корректным разбиением PTY stdin на предел `CNXN maxdata`. Исторические подробности — [source-audit](source-audit/).
- **REV38:** IME-композиция, русский текст, emoji и Backspace; [PR #31](https://github.com/Ncorror/NekoFlash-2.0/pull/31), [PR CI PASS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41235219229).
- **REV39:** системное выделение и копирование строк вывода; [PR #32](https://github.com/Ncorror/NekoFlash-2.0/pull/32), [CI PASS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41235417522).
- **REV40:** выделение блокирует нежелательную автопрокрутку; [PR #33](https://github.com/Ncorror/NekoFlash-2.0/pull/33), [CI PASS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41236741574).
- **REV41:** ограниченный общий срок ожидания Fastboot final после DATA (2-секундные чтения, общий срок 30 секунд); [PR #34](https://github.com/Ncorror/NekoFlash-2.0/pull/34), [CI PASS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41240926690). **Post-DATA на реальном устройстве не проверен.**
- **REV42:** разделение каждого generic ADB `A_WRTE` по peer `maxdata` без потери исходных байтов; [PR #35](https://github.com/Ncorror/NekoFlash-2.0/pull/35), [CI PASS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41241152461).
- **REV43:** потоковый трёхпроходный рекурсивный `adb push -r` без `toList()` всего дерева; внутренний `pushFile` больше не сбрасывает Stop между файлами; [PR #37](https://github.com/Ncorror/NekoFlash-2.0/pull/37). Физический рост RAM и USB detach остаются открытыми для тестов.

## Известные открытые проблемы

- **CI DEV API26**: `InterfacePolishAndroidTest.thousandRowsRecycleAndKeepOrderFormAndScrollAcrossRecreation` и `largeFontAndLandscapeKeepNavigationAndMassActionVisible` упали с `UI did not reach the expected state`. Вследствие этого часть UI-скриншотов не создана. См. [run #41248168147](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147) и [issue #16](https://github.com/Ncorror/NekoFlash-2.0/issues/16). Нельзя выдавать объединённую ветку за полностью зелёную.
- **[TERM-01 #20](https://github.com/Ncorror/NekoFlash-2.0/issues/20)**: задержка закрытия/клавиатура/поворот — нужен настоящий Android HOST.
- **[ADB #19](https://github.com/Ncorror/NekoFlash-2.0/issues/19)**: долгие оболочки, доказательство RAM/дисковой диагностики на физическом устройстве.
- **[ADB push #36](https://github.com/Ncorror/NekoFlash-2.0/issues/36)**: кодовый фикс REV43 слит, но реальная RAM, Stop, USB detach, 512+ файлов, старый ADB maxdata ещё ждут отчётов.
- Постоянные диагностические raw логи **не удаляем и не обрезаем**; в публичные Issue не выкладываем серийники, Mi-токены, ключи и приватные `trace-*.bin`.
- Проверка опасных операций `erase`, `format`, Fastboot DATA, Sideload и flashing — только отдельный согласованный этап на запасном разблокированном TARGET с резервной копией и планом восстановления. Защита release/GitHub переносится на финальный этап.

## Граница этой ревизии документации

REV44 добавляет офлайн-тестбук и обновляет актуальные индексные документы; PR #39 переносит рабочие изменения в `main` с сохранением обеих линий истории. **Исторические отчёты alpha10 и REV5/REV9/REV27 не переписываем:** даты, CI SHA и условия в них относятся только к указанным старым проверкам. `CHANGELOG.md` описывает опубликованные версии; DEV REV43 нельзя выдавать за новый официальный релиз.

---

## Архивная DEV-сводка (исторический REV27, 30 октября 2026)


Дата сверки: **30 октября 2026**. Речь только о репозитории `Ncorror/NekoFlash-2.0`, не о NekoFlash Pro и не о релизном APK старого `Ncorror/NekoFlash`.

## Где находится актуальный код

| Объект | Подтверждённое состояние |
|---|---|
| Рабочая ветка | `work/rev26-terminal-imports-20261030` |
| Последнее объединение | REV27, merge `8806222d85909dfd1bd4f8475b34ede87000458d`, [PR #14](https://github.com/Ncorror/NekoFlash-2.0/pull/14) |
| Предыдущее исправление | REV26, [PR #13](https://github.com/Ncorror/NekoFlash-2.0/pull/13), отмена зависшего импорта на Android API 26 |
| Проверенный коммит REV27 | `acde18d068652250f1c69a9e69c2d065f114ef3f`; содержимое дерева совпадает с merge |
| CI | [41012595076](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41012595076): build/JVM/lint/native PASS; 19 инструментальных тестов на API 26 и 19 на API 34; по 28 снимков интерфейса |
| Android APK | `app/build.gradle`: `6.0.0-alpha11`, `versionCode 234`; DEV-пакет `ru.forum.adbfastboottool.dev` |
| Публикация | REV27 **не является** новым production release; в `main` эта серия не объединена |
| Аппаратные ограничения | Новые REV26/27 не проверены на физической матрице устройств; проход CI не равен успешной прошивке |

Сводка является **снимком конкретной ревизии**, а не автоматически обновляемой страницей. При новом merge проверять фактическую ветку, SHA, GitHub Actions и сборочные файлы, прежде чем менять таблицу.

## Какие документы за что отвечают

- [README](../README.md) — текущее назначение проекта, версия и навигация.
- [CHANGELOG](../CHANGELOG.md) — только опубликованные версии и их история. Незавершённые DEV-ревизии не должны выдавать себя за release.
- [PRIVACY](../PRIVACY.md) — реальные пути хранения, Mi-сессии и границы удаления идентификаторов.
- [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md) и [vendor/filesystems](../vendor/filesystems/README.md) — лицензии и соответствующие исходники встроенных `mke2fs` и `mkfs.f2fs`.
- [SECURITY_REVIEW_BACKLOG](SECURITY_REVIEW_BACKLOG.md) — проблемы, которые **не исправляются документацией** и должны получить код/тесты.
- [HARDWARE_VALIDATION](HARDWARE_VALIDATION.md) — доказательства испытаний ранее опубликованной линии. Не переносить автоматически на REV27.
- [source-audit/](source-audit/README-FIRST-RU.md), [refactor/](refactor/REV11-CORE-CLEANUP-RU.md), [design-approved/](design-approved/README.md), [testing/](testing/REV9-BIG-DEVICE-TEST-RU.md) — исторические решения и проверки **на даты своих ревизий**, а не актуальные инструкции сборки.
- [GitHub Actions](../.github/workflows/build.yml) — проверка DEV; [release workflow](../.github/workflows/release.yml) — отдельная процедура подписанного выпуска.

## Что было реализовано в REV26–27

REV26 развела закрытие терминала и отмену операции, подавила посторонний старый 100% прогресс при командных задачах, сохранила PTY и историю команд; импорт SAF получил прерываемое ожидание источника. REV27 убрала лишнюю перерисовку `RecyclerView` при свёрнутом терминале и добавила тест на повторное открытие после большого вывода. Эти утверждения относятся к проверенному REV27 SHA и CI выше.

## Что осталось перед Release Candidate

1. **P0 — защита данных и опасные операции:** подтверждения для erase/format/flash; тесты санитайзера и копирования терминала; модель хранения сырых логов; атомарность ADB-ключа. [Открытые находки](SECURITY_REVIEW_BACKLOG.md).
2. **P1 — импорт и файлы:** APK/OBB, SAF grants, конфликт имён, отмена и очистка, доступное место, провайдеры с неточным размером. Прогон с реальными поставщиками SAF.
3. **P1 — USB/исполнение:** ADB Host/PTY, Fastboot/fastbootd, sideload, длинные передачи, detach/reconnect, watchdog/таймауты, проверка отсутствия busy-loop и неконтролируемого удержания памяти.
4. **P1 — интерфейс:** достоверная информация о подключённом устройстве, вкладки, системные клавиатуры, кнопки закрытия и остановки, полный русский/английский текст и accessibility.
5. **P0 перед релизом — аппаратная матрица:** отдельные согласованные испытания на разблокированных тестовых устройствах, сверка лога/SHA результатов, сохранение дампов/плана восстановления; не путать с эмуляторным CI.
6. **P0 перед релизом — release engineering:** версия/код, лицензии/исходники компонентов, workflow и права публикации, подпись постоянным сертификатом, установка/обновление, документированный rollback.

Приоритеты относятся к порядку работы, **не** означают, что каждая задача установлена как уязвимость определённой CVSS-категории.

## Правила для новых ревизий

1. Ветка `work/rev*` → reviewable PR → GitHub Actions → эмуляторы API 26/34 → только после PASS merge в DEV; не изменять `main` без отдельного решения.
2. В README указывать именно **последний фактически проверенный SHA и CI**. Исторические численные результаты оставлять в документе исходной ревизии, а не копировать как текущие.
3. Не удалять рабочие функции для «чистоты» рефакторинга; изменение протокола сопровождается отдельным тестом и USB-проверкой.
4. Документацию, хранение секретов и экспорт журналов проверять **до** публичного выпуска. Сырые идентификаторы и токены не коммитить и не прикладывать к публичному PR.
5. Изменения документации не доказывают устранение дефектов. Список открытых проблем обновлять только по фактически прошедшим тестам, с ссылкой на коммит и CI.
