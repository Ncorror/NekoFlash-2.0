<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Рабочая сборка REV49:** [PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45) обновляет операционный центр и временно скрывает «Прошивка → Инструменты». Опубликованный ниже набор REV45 и его скриншоты относятся к прежней версии; изменения доступны в комплекте успешного CI этого PR.

[English](README.md) · Русский

NekoFlash позволяет с одного Android-телефона обслуживать второй по USB OTG через ADB и Fastboot. На телефоне-хосте нужен Android 8.0 или новее, поддержка USB Host и кабель с передачей данных. Root на хосте не требуется.

**Идёт тестирование на устройствах · REV45**

Собираем отчёты по DEV-сборке REV45 для связок HOST → TARGET. Тестовая APK ставится рядом с оригинальным NekoFlash. Работа OTG, переподключение и запись разделов на этой сборке ещё требуют проверки на телефонах.

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
