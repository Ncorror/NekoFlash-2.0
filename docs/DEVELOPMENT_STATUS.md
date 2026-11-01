# NekoFlash 2.0 — тестирование REV44 в main, 1 ноября 2026

> **Аудит актуальной тестовой базы (REV45, 1 ноября 2026):** [полный чек-лист, реальные CI и 49 предупреждений lint](AUDIT-REV45-2026-11-01-RU.md). REV44 объединена в `main` через PR #39 (`34990ea14f21`). [Успешный интеграционный CI 41342607390](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342607390) не отменяет [падение следующего CI 41342739222](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342739222): на API 26 два теста с клавиатурой. Исправление тестовой изоляции и диагностические lint-артефакты — [PR #40](https://github.com/Ncorror/NekoFlash-2.0/pull/40); считать его исправленным только после нового CI. Официальный подписанный release не создавался.

**Это текущая точка входа.** Предыдущие ревизии и их CI сохранены в исторических разделах ниже. Не переносить старые PASS на REV43.

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

[**Скачать автономный REV43 HTML прямо из main**](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html). Он поддерживает RU/EN, этапы, чекбоксы, статусы и свободные замечания, JSON-прогресс, ZIP-отчёт с исходными `log-*`, `trace-*.bin`, `session-summary-*` и фотографиями. Он не отправляет файлы на сервер и требует ручного разрешения на папку логов. Каждый успешный build CI также экспортирует тестбук как отдельный скачиваемый GitHub Actions artifact. Для тестовой группы указан точный идентификатор **NekoFlash Insiders**, но ссылка-приглашение не опубликована и не должна подставляться наугад.

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
