<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — REV5: Five-Tab Audit & Implementation Roadmap

### Verification of all five tabs against original legacy baseline and implementation roadmap

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Overview

**Date:** October 10, 2026.  
**Status:** Source code inspection + initial port of one approved screen into working copy. Neither APK builds, GitHub release pushes, USB operations, nor live Mi account authentication were performed at this stage.

## 1. Provenance Control

- **Single Canonical Original:** `NekoFlash-main-legacy.zip`, SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`.
- From `app/build.gradle`: `applicationId=ru.forum.adbfastboottool`, `versionName=6.0.0-alpha11`, `versionCode=234`, `compileSdk=36`, `targetSdk=34`; version string is intentionally **unchanged** to avoid presenting a work-in-progress checkpoint as finished 2.0 release.
- Projects such as `NekoFlash-Pro`, remote experimental branches, and side forks were NOT used.
- Decision precedence: Master REV2 → Sideload REV3 approved → Unlock REV4 approved. **Third tab is Sideload**, despite historical internal identifier `adb` in code.

## 2. Five-Tab Verification Matrix

| Tab | Approved UX Specification | Original Android Code Baseline | Gap & Plan |
| --- | --- | --- | --- |
| **Home** | Device card only with photo & specs; actual codename, Android version, bootloader status, active slot; two independent expandable sections; no active operations | `page_home.xml`: `cardHomeDeviceInfo`, mode/product/slot/unlocked/maxDownload fields, Refresh button; plus **persistent** `cardOperationCenter` | Remove persistent GUI Operation Center from Home **after** migrating state presentation into contextual Center. Add device catalog & freshness indicator; never invent hardware IDs. |
| **Flash** | Approved **Quick Flash**, **Mass Flash**, and **Fastboot Tools** with segmented sub-section switching; no auto-reboot or separate forced preflight dialog | `page_fastboot.xml`: recovery-first card, individual partition flash buttons; `DeviceViewModel` contains `flashOperationDraft`, `runFlashQueue`; Fastboot operations and diagnostics exist | Replace UI completely **only with approved layout composition**. Bind to existing methods without rewriting transport. Resolve target/slot/format/fetch/fastbootd wire differences separately from design. |
| **Sideload** | REV3: Recovery instruction prompt, selected ZIP filename, "Choose ZIP for Sideload", "Import file" — and nothing else | `page_adb.xml` already contains both buttons; `MainActivity.kt` calls `showFileSelector → viewModel.runSideload(file)` and `startImportFilePicker` | **First UI slice implemented in REV5:** minimal screen with chosen ZIP filename and preserved button IDs/handlers. Full Recovery verdict integration and Operation Center decoupling follow next. No Push/Pull/APK/Shell in this tab. |
| **Unlock** | REV4: Mi Account, bootloader status, server zone hidden in expandable advanced settings, wipe warning, action button enabled only under verified conditions, verdict confirmed upon reconnect (`unlocked=yes`) | `MainActivity.buildUnlockPage()` dynamically creates views; `MiLoginActivity`, `MiAccountClient`, `MiUnlockClient`, `DeviceViewModel.runMiUnlock`, and pending verification logic exist | Migrate **without replacing backend** into approved REV4 layout. Move zone selection into expandable section; link pending-result to same history entry. Never leak secrets into shared logs. Never execute unlock on hardware without owner consent. |
| **Settings** | Approved placement for "History & Reports", language, and system parameters. Independent new log viewer screen is not approved | `MainActivity.buildSettingsPage()` manages language, permissions, battery optimization, workspace cache clearing, About; logs accessible via legacy Console/Home | Add agreed entry point "History & Reports", migrated from Home. Clarify storage/streaming implementation before removing legacy structures; do not introduce unauthorized extra sections. |

## 3. Global Shell & Cross-Cutting Invariants

- `activity_main.xml` already contains **five tabs**. The third tab label is presented as "Sideload" in `values/strings.xml` and `values-ru/strings.xml`, though internal layout IDs (`tabAdb`) and routing keys retain `adb`. Do not rename internal identifiers without complete call site auditing.
- **USB Status:** Legacy uses separate `tvStatus`, OTG strip, and scan/cancel buttons. Convert into a unified **clickable status strip** with live detected mode and technical details bottom sheet; avoid guessing Fastbootd/Recovery modes upon reconnect.
- **Terminal:** Legacy console is a BottomSheet in `activity_main.xml` (`ConsoleDockController`). In approved design, terminal opens via **icon to the right of USB strip**, occupies the full workspace, defaults to HOST CLI, reserves PTY strictly for `adb shell`, preserving copy/paste/IME. Terminal commands must never launch the GUI Operation Center.
- **Operation Center:** Legacy contains persistent Home card and status strip. Approved design specifies a contextual modal dialog and a compact floating pill above the bottom navigation; true progress stems only from backend core, single USB ownership, and user-confirmed queuing on busy transport.
- **Welcome:** Retain original `bg_welcome.jpg` artwork; no mandatory onboarding storage gates; window insets and cold-start permissions handled cleanly.

## 4. Critical Boundaries to Close Before Physical Qualification

| Priority | Legacy State | Remediation / Verification Requirement |
| --- | --- | --- |
| P0 | USBFS enforces hard 90-second timeout (`FastbootProtocol.kt`, `native_usbfs.cpp`) | Characterize stall deadline vs overall timeout; avoid breaking slow large RAW/sparse transfers. Require progress verification and safe cancellation test. |
| P0 | `DiagnosticLogStore.kt` purges segments (caps at 30 files / 64 MiB); `SanitizedLogShare.kt` caps at 64 MiB | Design streaming write/export, user-managed log retention, visible out-of-space warnings; protect auth secrets outside public reports. |
| P0 | `DeviceViewModel.startOperation` executes under ViewModel scope; `FlashOperationService` is mostly a notification | Strengthen durable execution ownership and USB serialization; never allow concurrent secondary USB sessions. |
| P1 | `verifyPendingSideloadIfReady()` logs SUCCESS/FAILED, but does not promote the operation outcome | Atomically update GUI operation and history **only on authoritative verdict**; keep UNKNOWN as pending, not failure. |
| P1 | Mi Unlock pending check may diverge on timeout vs report | Conclude operation state reliably upon reconnect/timeout, match device identity, produce single consolidated report. |
| P1 | `FlashOperationDraftPolicy` caps at 32 operations | Remove artificial UI limit; document technical limits; execute queue strictly sequentially, stopping upon first failure. |
| P1 | Quick Flash UI is recovery-first | Bind approved partition selector, exact manual partition input, actual slot resolution, single "Flash" button, manual Reboot menu. |
| P1 | No qualified hardware tests of 2.0 APK yet | First execute fake transport + clean builds, then non-destructive hardware checks on unlocked test device, followed by full test suite. |

## 5. Current Changes in Android Source Tree

In the Android source tree, **only five files were changed/added**:

1. `res/layout/page_adb.xml`: Minimal Sideload REV3 layout (title, Recovery hint, chosen ZIP name, two buttons, verdict note).
2. `res/drawable/bg_sideload_file.xml`: Clean outline background for file display.
3. `res/values/strings.xml`, `res/values-ru/strings.xml`: RU/EN localized strings without breaking existing keys.
4. `MainActivity.kt`: Passes chosen ZIP name to `tvSideloadSelectedZip` and calls original `viewModel.runSideload(file)`.

*Note:* Legacy UX starts sideload **immediately after file selection in `showFileSelector`**. REV5 preserves this exact working behavior. "Import file" only imports and does not start sideload.

Core engine, AndroidManifest, Welcome, Home, Flash, Unlock, Settings, Gradle configuration, and TabController remain byte-for-byte identical.

## 6. Sequential Implementation Roadmap

1. **R5 (Current):** Immutable legacy baseline, five-tab verification matrix, port REV3 Sideload, offline XML/contract tests.
2. **R6:** Global shell/UI + unified USB status bar, mode detection, window insets, and full-screen terminal entry. Preserve USB engine.
3. **R7:** Home (device specifications catalog and freshness indicators, without Home Operation Center), followed by three approved Flash sub-screens with authentic Fastboot operations.
4. **R8:** REV4 Unlock with canonical Xiaomi backend, Settings → History & Reports, contextual Operation Center modal/queue and robust background execution.
5. **R9:** Regression/CI qualification, large-file transfers, reconnect recovery, dual localization verification, physical device testing on unlocked hardware, followed by release evaluation.

## 7. Verification Checks

- Run `python3 tests/check_rev5.py` for automated structural verification of XML resources, RU/EN string parity, IDs/handlers, and transport file SHA256 integrity against baseline.

---

## Русская версия / Russian Version

# NekoFlash 2.0 — REV5: сверка пяти вкладок с ORIGINAL и карта внедрения

Дата: 10 октября 2026 г. Статус: **проверка по исходникам + начат перенос одного утверждённого экрана в копию проекта**. Ни APK, ни GitHub, ни USB, ни настоящая Mi-авторизация здесь не использовались.

## 1. Контроль происхождения

- **Единственный оригинал:** `NekoFlash-main-legacy.zip`, SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`.
- Из `app/build.gradle`: `applicationId=ru.forum.adbfastboottool`, `versionName=6.0.0-alpha11`, `versionCode=234`, `compileSdk=36`, `targetSdk=34`; версия **не изменена**, чтобы не выдавать прототип за готовый выпуск 2.0.
- Проекты `NekoFlash-Pro`, удалённые ветки и экспериментальные репозитории не применялись.
- Приоритет решений: master REV2 → Sideload REV3 approved → Unlock REV4 approved. **Третья вкладка Sideload**, несмотря на историческое внутреннее имя `adb` в коде.

## 2. Сверка всех пяти вкладок

| Вкладка | Утверждённый UX | Что есть в оригинальном Android-коде | Разрыв и план |
| --- | --- | --- | --- |
| **Главная** | Только карточка устройства с фото и характеристиками; реальный codename, Android, bootloader, slot; два независимых раскрытия; без операций | `page_home.xml`: `cardHomeDeviceInfo`, поля mode/product/slot/unlocked/maxDownload, кнопка Refresh; и **постоянный** `cardOperationCenter` | Удалить постоянный GUI Центр операций с Home **после** переноса состояния в контекстный Центр. Добавить каталог моделей и актуальность данных; не выдумывать идентификаторы. |
| **Прошивка** | Утверждены **Быстрая**, **Массовая**, **Инструменты Fastboot** с переключением соответствующего раздела; нет автоперезагрузки или отдельного preflight | `page_fastboot.xml`: recovery-first card, отдельные кнопки boot/recovery/init_boot и пр.; `DeviceViewModel` содержит `flashOperationDraft`, `runFlashQueue`; операции Fastboot и диагностика есть | UI полностью заменить **только на уже утверждённую композицию**. Привязать к существующим методам без переписывания transport. Исправить target/slot/format/fetch/fastbootd и реальные протокольные ответы отдельно от дизайна. |
| **Sideload** | REV3: инструкция Recovery, имя ZIP, «Выбрать ZIP для Sideload», «Импортировать файл» — и ничего более | `page_adb.xml` уже содержит обе кнопки; `MainActivity.kt` вызывает `showFileSelector → viewModel.runSideload(file)`, `startImportFilePicker` | **В REV5 реализован первый UI-срез:** минимальный экран с именем выбранного ZIP и прежними ID/обработчиками. Полный проверенный результат Recovery и чистый Operation Center — последующий этап. Никаких Push/Pull/APK/Shell во вкладке. |
| **Unlock** | REV4: Mi Account, статус загрузчика, зона скрыта в дополнительных параметрах, предупреждение удаления, кнопка только при реальных условиях, итог после reconnect `unlocked=yes` | `MainActivity.buildUnlockPage()` конструирует элементы, `MiLoginActivity`, `MiAccountClient`, `MiUnlockClient`, `DeviceViewModel.runMiUnlock`, pending verification уже присутствуют | Перенести **без замены backend** в утверждённую композицию REV4. Убрать видимость зоны из первого уровня; связать pending-result с той же записью в истории. Секреты не выводить в shared logs. Реальную разблокировку не выполнять без отдельного разрешения владельца. |
| **Настройки** | Согласованы размещение «История и отчёты» и язык/общесистемные параметры. Самостоятельный новый экран просмотрщика логов не утверждён | `MainActivity.buildSettingsPage()` содержит язык, разрешения, батарею, очистку рабочего каталога, About; logs доступны через старую Console/Home | Добавить согласованную точку «История и отчёты», перенести её из Home. Согласовать реализацию полного хранения/стриминга прежде чем убирать старое; новых разделов не придумывать. |

## 3. Общая оболочка (сквозные условия)

- `activity_main.xml` уже содержит **пять вкладок**. Кнопка третьей реально отображается как «Sideload» в `values/strings.xml` и `values-ru/strings.xml`, хотя ID `tabAdb` и маршрутизатор внутри пока называются `adb`. Не переименовывать внутренние ключи без полной ревизии call sites.
- **USB:** в legacy отдельный `tvStatus`, OTG-полоса, scan/cancel. Нужно превратить в единую **кликабельную панель** с реальным mode и подробной шторкой; не допускать догадок о Fastbootd, Recovery, устройстве при reconnect.
- **Терминал:** legacy console — `BottomSheet` в `activity_main.xml`, `ConsoleDockController`. В согласованном проекте терминал открывается **иконкой справа от USB**, занимает всю рабочую область, HOST CLI по умолчанию, PTY — только `adb shell`, ввод/вставка/IME сохранены. Никакие команды терминала не вызывают Центр операций.
- **Центр операций:** legacy содержит постоянный Home card и status strip. Утверждён контекстный GUI-центр и компактный плавающий индикатор над нижней навигацией; реальный progress только из ядра, один USB owner, очередь по подтверждению при занятости.
- **Welcome:** сохраняется оригинальное изображение `bg_welcome.jpg`; нет обязательного storage/risks onboarding; insets и доступ на холодном старте — отдельная задача.

## 4. Дефекты/границы, обязательные к закрытию до большого теста

| Приоритет | Проверено в legacy | Требование исправления / теста |
| --- | --- | --- |
| P0 | USBFS содержит абсолютный 90-секундный timeout (`FastbootProtocol.kt`, `native_usbfs.cpp`) | Разобрать stall vs overall; не ломать медленные большие RAW/sparse передачи. Только с тестом прогресса и корректной отмены. |
| P0 | `DiagnosticLogStore.kt` удаляет старые файлы, пределы 30 / 64 МиБ; `SanitizedLogShare.kt` имеет предел 64 МиБ | Разработать потоковую запись/экспорт, пользовательское управление удалением, явное сообщение о нехватке места; сохранять секреты из очищенной копии вне отчёта. |
| P0 | `DeviceViewModel.startOperation` выполняет работу под ViewModel; `FlashOperationService` преимущественно уведомление | Укрепить длительное владение операцией и сериализацию USB; не допускать второй параллельной USB-сессии. |
| P1 | `verifyPendingSideloadIfReady()` логирует SUCCESS/FAILED, но не гарантирует соответствующего итогового статуса операции | Обновлять ту же GUI-операцию и историю **только при достоверном verdict**; UNKNOWN не считать успехом. |
| P1 | Mi Unlock проверяет pending, однако таймаут/отчёт могут расходиться | Завершать состояние операции после reconnect/timeout, сопоставлять устройство, писать один итоговый отчёт. |
| P1 | `FlashOperationDraftPolicy` имеет cap 32 | Не переносить UI-ограничение; любые технические пределы документировать; очередь исполнять последовательно и останавливаться на первой FAIL. |
| P1 | Quick Flash UI `recovery-first` | Подключить утверждённый список разделов, ручное точное имя, actual slot resolution, одну кнопку «Прошить», ручное меню Reboot. |
| P1 | Нет доказанных тестов APK 2.0 на устройстве | Сначала fake transport + чистая сборка, потом только неразрушающие проверки на разблокированном аппарате, далее большой тест по согласованному перечню. |

## 5. Выполнено сейчас в копии Android-исходников

В `NekoFlash-2.0-source` **только пять изменённых/добавленных файлов** (полный список `CHANGED-FILES.txt`):

1. `res/layout/page_adb.xml`: минимальная компоновка Sideload REV3 (название, Recovery hint, имя ZIP, две кнопки, примечание о verdict).
2. `res/drawable/bg_sideload_file.xml`: прозрачный контур отображения файла.
3. `res/values/strings.xml`, `res/values-ru/strings.xml`: RU/EN строки без удаления старых.
4. `MainActivity.kt`: имя выбранного ZIP передаётся в `tvSideloadSelectedZip`, затем вызывается **исходный** `viewModel.runSideload(file)`.

Важно: исторический UX оригинала запускает Sideload **сразу после выбора файла в `showFileSelector`**. REV5 сохраняет именно эту рабочую семантику. Отдельную кнопку запуска не добавляли. `Импортировать файл` только импортирует; не начинает Sideload.

**Движок, Manifest, Welcome, Главная, Прошивка, Unlock, Настройки, Gradle и внутренний TabController побайтово не менялись.** Скачанный архив оригинала не изменён. Эта правка — первый шаг реализации, **не релиз / не APK**.

## 6. Последовательность реализации (без самовольного редизайна)

1. **R5 (сейчас):** immutable legacy, матрица всех вкладок, перенос REV3 Sideload, локальные XML/contract тесты.
2. **R6:** общее shell/UI + единая USB-панель, mode state, insets и вход в полноэкранный терминал. USB engine не трогать.
3. **R7:** Главная (каталог фактов/моделей и признак свежести, без Home Center), затем утверждённые три экрана Прошивки с сохранением подлинных Fastboot операций.
4. **R8:** REV4 Unlock с оригинальным Xiaomi backend, Настройки → История и отчёты, новый контекстный GUI Центр операций/очередь и длительная работа в фоне.
5. **R9:** regression/CI, большие файлы, восстановление после reconnect, два языка, телефонный тест разблокированного устройства и только после этого оценка готовности APK.

На каждом шаге `AdbProtocol`, `FastbootProtocol`, native USBFS и identity USB owner сверяются с baseline; изменения в них — только целевыми исправлениями с отдельными тестами. Текущую реализованность не путать с утверждением дизайна.

## 7. Проверка этого пакета

- `python3 tests/check_rev5.py` — автоматическая структурная проверка XML, RU/EN строк, ID и обработчиков, неизменности всех критичных транспортных файлов относительно SHA256 baseline.
