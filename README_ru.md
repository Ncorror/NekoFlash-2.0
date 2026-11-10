<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Подготовка Insiders-релиза — 11 ноября 2026:** `6.0.0-alpha11` (versionCode 234), проверенная база [`faebd3a`](https://github.com/Ncorror/NekoFlash-2.0/commit/faebd3a338ecd695c9ec4a879fa6a8c92db79fba), [CI `main` #42890625837 SUCCESS](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42890625837). PR #79–#82 слиты. **Комплект тестировщика:** использовать последний успешный запуск `main`; [проверенный ZIP APK + TestBook для этой базы](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42890625837/artifacts/13799940338) содержит **debug/Insiders**, а не подписанный production-релиз. Опубликованный ранее `insiders-rev45` — исторический. Большой ADB Pull с `USB_IN_FAILED / EPROTO` остаётся открытой аппаратно-зависимой проблемой ([#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72)); экспериментальный [PR #83](https://github.com/Ncorror/NekoFlash-2.0/pull/83) **не слит и не входит в релиз**. Отказ на паре POCO F7 (кастомное ядро, HOST) → POCO X3 Pro (TARGET) не доказывает общий отказ ADB на всех устройствах; нужны независимые отчёты сообщества. Для **production** alpha11 по-прежнему необходимы APK с постоянной подписью на финальном SHA, проверка сертификата и безопасный smoke-тест.

**Безопасный Sideload уже в `main`.** PR [#74](https://github.com/Ncorror/NekoFlash-2.0/pull/74) слит: выбор ZIP ничего не отправляет; передача начинается только отдельной кнопкой **Start Sideload** и после подтверждения. SAF-документ сохраняется для повтора, если Android выдал постоянный доступ. Старые упоминания PR #71 ниже — исторические снимки, а не текущее поведение.

**Граница релиза:** оставшиеся открытые задачи уже вынесены после релиза/в backlog: крайние UI-сценарии терминала [#20](https://github.com/Ncorror/NekoFlash-2.0/issues/20), дизайн Operation Center [#46](https://github.com/Ncorror/NekoFlash-2.0/issues/46), полевая перепроверка force-stop [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47), плавающий DocumentsUI [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48), специфичный для vayu `fastboot -w`/cache [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64) и большой ADB Pull EPROTO [#72](https://github.com/Ncorror/NekoFlash-2.0/issues/72). Fail-closed регрессия Fastboot из #67 и консервативная фиксация смены Recovery из #68 включены в этот alpha11-кандидат через PR #76. PR #73 с экспериментами большого ADB Pull по-прежнему явно исключён. Уже принятые разрушительные Mass Flash/Recovery/USB тесты повторять ради старых галочек не нужно.

**Историческая проверенная Insiders-сборка:** Actions #653 / artifact `13757708292`, SHA-256 APK `46230d199aa4eba93cb219b878c6395de9fd41ca1bfa2c042d096b663410f677` — прежний baseline. Бери **последний успешный tester kit из `main`** по ссылке ниже: каждый комплект содержит собственные SHA APK и исходников. См. [релизный аудит](docs/RELEASE_READINESS-2026-11-10.md).

**Тестировщикам:** брать только **успешный актуальный [комплект APK + testbook.html из одного `main` CI](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess)** и сверять SHA-256/run ID. До смены DEV сохранять старые ZIP диагностики и JSON отметок. **Публикация REV45** и её скриншоты ниже — исторические, не новый тестовый APK. Отдельный HTML прямо в `main` может содержать старые reference SHA: точные данные нового APK находятся в HTML из соответствующего CI ZIP.

[English](README.md) · Русский

NekoFlash позволяет с одного Android-телефона обслуживать второй по USB OTG через ADB и Fastboot. На телефоне-хосте нужен Android 8.0 или новее, поддержка USB Host и кабель с передачей данных. Root на хосте не требуется.

**Опубликованный контрольный набор REV45 (архивный)**

Ссылка на опубликованный ниже набор ведёт на старую DEV-сборку REV45, а не на исходники REV55. Тестовая APK ставится рядом с оригинальным NekoFlash. Работа OTG, переподключение и запись разделов на этой сборке ещё требуют проверки на телефонах.

**[Скачать комплект тестировщика Insiders (ZIP)](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip)** · [Сборки на GitHub Actions](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) · [NekoFlash Insiders · Telegram](https://t.me/+NR_FiUmvXlViOTUy)

Распакуй ZIP на телефоне-**хосте**. Установи debug APK и открой `testbook.html` в браузере. В ZIP два файла. Инструкция RU/EN, SHA-256 приложения и ссылка на Telegram-группу находятся в тестбуке. Чтобы взять снимок более свежей main для отдельного теста, войди в GitHub, открой успешный запуск main и выбери **NekoFlash-Insiders-Tester-Kit** в разделе Artifacts.

[Инструкция тестировщика](docs/insiders/FIELD-GUIDE-RU.md) · [Offline testbook RU/EN](docs/insiders/testbook.html)

Для одного раунда раздай всем один и тот же ZIP. У старой REV43 и контрольной REV45 разные ключи debug-подписи. Если уже стоит прежняя DEV, сохрани её журналы и экспортируй прогресс тестбука до переустановки.

## Первый тест

Сначала проверь интерфейс и клавиатуру. Затем подключи второй телефон, подтверди на нём разрешение ADB и выполни `adb devices` и `adb shell getprop ro.product.model`. Запиши обе модели, версии Android, build ID и фактический результат. ZIP с исходными логами передай тестовой команде в личном канале.

> Прошивка, стирание, форматирование и разблокировка могут удалить данные и лишить TARGET возможности загрузиться. Для записи нужен отдельный согласованный тест, запасное устройство, резервная копия и проверенный способ восстановления.

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

Прошли 308 JVM-тестов и по 20 инструментальных тестов на API 26 и 34. Lint завершился без замечаний. В каждом новом комплекте точный коммит и SHA-256 его APK записаны в `testbook.html`.

[Audit / Аудит](docs/insiders/AUDIT-2026-11-02.md) · [Hardware](docs/HARDWARE_VALIDATION.md) · [USB OTG](docs/USB_CONNECTION_TROUBLESHOOTING.md) · [Privacy](PRIVACY.md)

Для локальной debug-сборки нужны JDK 17 и сборка утилит файловых систем. Точные шаги есть в [сборочном workflow](.github/workflows/build.yml).

## Проект

Автор и разработчик — [Ncorror](https://github.com/Ncorror). Участник проекта — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Предыдущий README и история релизов](https://github.com/Ncorror/NekoFlash-2.0/blob/7b54ba1e70807bdc7cee65a64cff87fd002ce26b/README_ru.md)

Теги — `android` `usb-otg` `usb-host` `adb` `fastboot` `adb-sideload` `android-recovery` `hardware-testing`
