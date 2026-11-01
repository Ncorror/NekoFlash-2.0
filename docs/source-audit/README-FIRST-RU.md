> **ARCHIVE / АРХИВ REV5:** bilingual contributor documentation is retained as an historical baseline. The up-to-date testing references are [DEV status](../DEVELOPMENT_STATUS.md), [Insiders testbook](../insiders/testbook.html), [RU guide](../testing/INSIDERS-FIELD-TEST-REV43-RU.md) / [EN guide](../testing/INSIDERS-FIELD-TEST-REV43-EN.md). Legacy ZIP paths and REV5 test outcomes must not be treated as REV43 hardware evidence.

<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 REV5 — Getting Started & Continuation Point

### Baseline verification, local test instructions, and next steps

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Overview

**Baseline:** ORIGINAL `NekoFlash-main-legacy.zip` (SHA256 recorded in `LEGACY-SOURCE-SHA256.json`). NOT NekoFlash Pro.

**Design Consensus:** REV2 (general screens), REV3 **Sideload — APPROVED**, REV4 **Unlock — APPROVED**. None of the agreed screens were redesigned from scratch.

**Actual Android Code Changes at this step:** Only the approved Sideload REV3 tab (XML, 2 localizations, selected ZIP filename display + legacy execution invocation). The full application is NOT built or qualified yet. The source code version remains `versionName=6.0.0-alpha11` — this is NOT a finished NekoFlash 2.0 release.

**Key Documentation:** Read `NEKOFLASH2-REV5-AUDIT-AND-ROADMAP-RU.md`, then `CHANGED-FILES.txt`, `SIDELOAD-REV3-IMPLEMENTATION.patch`; sources: repository root.

**Quick Offline Verification:** Run `python3 tests/check_rev5.py` from repository root. Standard Python 3 is required.

**Android Build:** Requires JDK 17, Android SDK 36, CMake/NDK, Gradle Wrapper from original source: `./gradlew :app:assembleDebug`. In environments without Android SDK, build and USB tests are skipped.

**Next Coding Milestone:** Unified USB panel + full-screen terminal (preserving USB backend and approved UI) and removal of the persistent Home Operation Center **only after migrating status presentation**.

**Important Safety Note:** REV4 does not mean real bootloader unlocking was performed or that the Xiaomi API is currently operational. Never trigger unlock on hardware without explicit authorization from the device owner.

---

## Русская версия / Russian Version

# NekoFlash 2.0 REV5 — точка продолжения

**Основа:** ORIGINAL `NekoFlash-main-legacy.zip` (SHA256 указан в `LEGACY-SOURCE-SHA256.json`). НЕ NekoFlash Pro.

**Согласование дизайна:** REV2 (общие экраны), REV3 **Sideload — утверждено**, REV4 **Unlock — утверждено**. Ни один из согласованных экранов заново не проектировался.

**Реальные изменения Android-кода на этом шаге:** только утверждённая вкладка Sideload REV3 (XML, 2 локализации, выбранное имя ZIP + прежний запуск). Приложение целиком НЕ собрано и не протестировано. В исходном коде по-прежнему `versionName=6.0.0-alpha11` — это НЕ готовая версия NekoFlash 2.0.

**Что читать:** `NEKOFLASH2-REV5-AUDIT-AND-ROADMAP-RU.md`, затем `CHANGED-FILES.txt`, `SIDELOAD-REV3-IMPLEMENTATION.patch`; исходники: `NekoFlash-2.0-source/`.

**Быстрый офлайн-тест:** из корня ZIP после распаковки `python3 NekoFlash-2.0-source/tests/check_rev5.py`. Требуется стандартный Python 3.

**Android build:** нужен JDK 17, Android SDK 36, CMake/NDK, Gradle Wrapper из оригинала; `cd NekoFlash-2.0-source && ./gradlew :app:assembleDebug`. В локальном контейнере SDK нет, сборка/установка и USB-тесты НЕ выполнялись.

**Следующий кодовый этап:** единая USB-панель + полноэкранный терминал (с сохранением USB backend и approved UI) и удаление постоянного Центра операций из Home **только после переноса отображения статуса**.

**Отдельно:** REV4 не означает, что выполнена реальная разблокировка или Xiaomi API сейчас работоспособен. Никогда не запускать unlock на устройстве без отдельного прямого разрешения владельца.
