# NekoFlash 2.0 REV5 — точка продолжения

**Основа:** ORIGINAL `NekoFlash-main-legacy.zip` (SHA256 указан в `LEGACY-SOURCE-SHA256.json`). НЕ NekoFlash Pro.

**Согласование дизайна:** REV2 (общие экраны), REV3 **Sideload — утверждено**, REV4 **Unlock — утверждено**. Ни один из согласованных экранов заново не проектировался.

**Реальные изменения Android-кода на этом шаге:** только утверждённая вкладка Sideload REV3 (XML, 2 локализации, выбранное имя ZIP + прежний запуск). Приложение целиком НЕ собрано и не протестировано. В исходном коде по-прежнему `versionName=6.0.0-alpha11` — это НЕ готовая версия NekoFlash 2.0.

**Что читать:** `NEKOFLASH2-REV5-AUDIT-AND-ROADMAP-RU.md`, затем `CHANGED-FILES.txt`, `SIDELOAD-REV3-IMPLEMENTATION.patch`; исходники: `NekoFlash-2.0-source/`.

**Быстрый офлайн-тест:** из корня ZIP после распаковки `python3 NekoFlash-2.0-source/tests/check_rev5.py`. Требуется стандартный Python 3.

**Android build:** нужен JDK 17, Android SDK 36, CMake/NDK, Gradle Wrapper из оригинала; `cd NekoFlash-2.0-source && ./gradlew :app:assembleDebug`. В локальном контейнере SDK нет, сборка/установка и USB-тесты НЕ выполнялись.

**Следующий кодовый этап:** единая USB-панель + полноэкранный терминал (с сохранением USB backend и approved UI) и удаление постоянного Центра операций из Home **только после переноса отображения статуса**.

**Отдельно:** REV4 не означает, что выполнена реальная разблокировка или Xiaomi API сейчас работоспособен. Никогда не запускать unlock на устройстве без отдельного прямого разрешения владельца.
