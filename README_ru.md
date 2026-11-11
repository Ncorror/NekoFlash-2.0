<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Последняя проверенная точка main:** `7273b014`, `6.0.0-alpha11`; [CI #698 SUCCESS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/43068208355): JVM, Lint, Android API 26 и 34. Следующий комплект Insiders содержит **только debug APK** с проверенной контрольной суммой. Тесты и результаты теперь ведём **в чате команды**, без отдельного HTML-трекера. Полные диагностические ZIP экспортируем из NekoFlash.

**Ранее опубликованный Insiders REV50** ([историческая страница релиза](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/insiders-rev50)) собран из предыдущего коммита. Это не новая main и не production APK с постоянной подписью; старые материалы не перепаковываем.

**Новые исправления:** «Очистить» выбранный ZIP в Sideload без удаления файла, индикатор авторизации Mi-аккаунта, версия Android после подключения ADB SYSTEM. Отдельно от релиза оставляем [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72) ADB Pull EPROTO и [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64) Fastboot `-w`/cache. Экспериментальный [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83) не сливаем.

Русский · [English](README.md)

NekoFlash работает с ADB и Fastboot с одного Android-телефона по OTG на другом. Хост: Android 8+, USB Host, кабель данных. Root необязателен.

**Сборки тестировщикам:** [зелёные CI main](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) · [Telegram Insiders](https://t.me/+NR_FiUmvXlViOTUy) · [история реальных тестов](docs/testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). Бери APK из точного CI, указывай build ID и отправляй скриншоты с полным диагностическим ZIP. Уже проверенные прошивки и поворот терминала не повторяем.

## Интерфейс приложения

В консоли доступны ADB shell, push/pull и установка APK. На вкладке прошивки собраны Quick Flash, очередь Mass Flash и инструменты Fastboot. Есть Recovery Sideload, Xiaomi Unlock, отчёты операций и исходные журналы USB.

Английский интерфейс опубликованной APK REV45 на эмуляторе Android 14 (API 34). На главной виден хост без подключённого устройства. В очередь Mass Flash добавлен тестовый образ.

| Главная | Терминал | Быстрая прошивка |
| --- | --- | --- |
| <img src="docs/screenshots/rev45/portrait-home.png" width="210" alt="Главная"> | <img src="docs/screenshots/rev45/terminal-command.png" width="210" alt="Терминал"> | <img src="docs/screenshots/rev45/portrait-quick.png" width="210" alt="Быстрая прошивка"> |

| Очередь Mass Flash | Инструменты Fastboot | ADB Sideload |
| --- | --- | --- |
| <img src="docs/screenshots/rev45/mass-queue.png" width="210" alt="Очередь Mass Flash"> | <img src="docs/screenshots/rev45/portrait-tools.png" width="210" alt="Инструменты Fastboot"> | <img src="docs/screenshots/rev45/portrait-sideload.png" width="210" alt="ADB Sideload"> |

## Сборка и проверки

Сборка для сверки и скриншотов — `6.0.0-alpha11-dev` (234), `ru.forum.adbfastboottool.dev`, [`a31dedadd4ad`](https://github.com/Ncorror/NekoFlash-2.0/commit/a31dedadd4adcf08691ece82c7574f05286078f3). [CI #413](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886) · [APK ZIP](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41438797886/artifacts/13139380119).

Прошли 308 JVM-тестов и по 20 инструментальных тестов на API 26 и 34. Lint завершился без замечаний. Точный SHA-256 APK и исходный коммит подтверждаются в соответствующем CI.

[Audit / Аудит](docs/insiders/AUDIT-2026-11-02.md) · [Hardware](docs/HARDWARE_VALIDATION.md) · [USB OTG](docs/USB_CONNECTION_TROUBLESHOOTING.md) · [Privacy](PRIVACY.md)

Для локальной debug-сборки нужны JDK 17 и сборка утилит файловых систем. Точные шаги есть в [сборочном workflow](.github/workflows/build.yml).

## Проект

Автор и разработчик — [Ncorror](https://github.com/Ncorror). Участник проекта — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Предыдущий README и история релизов](https://github.com/Ncorror/NekoFlash-2.0/blob/7b54ba1e70807bdc7cee65a64cff87fd002ce26b/README_ru.md)

Теги — `android` `usb-otg` `usb-host` `adb` `fastboot` `adb-sideload` `android-recovery` `hardware-testing`
