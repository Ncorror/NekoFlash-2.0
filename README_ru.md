<div align="center">

<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="Логотип NekoFlash" />

# NekoFlash 2.0

### Инструмент для прошивки и обслуживания Android через USB OTG (ADB и Fastboot)

[English](README.md)&nbsp;&nbsp;|&nbsp;&nbsp;Русский

</div>

## Актуальная тестовая ветка main — NekoFlash 2.0 REV44 (1 ноября 2026)

Сюда переносим REV26–44 для испытаний Android HOST → TARGET. Это **тестовая DEV APK**, не подписанный стабильный релиз; установленный старый NekoFlash удалять не нужно (пакет `ru.forum.adbfastboottool.dev`).

**Telegram-группа тестирования — NekoFlash Insiders.** В группе распространяем тестовую APK и тестбук, собираем предложения и приватные ZIP с логами. Достоверная ссылка-приглашение пока не предоставлена, поэтому адрес `t.me` не придумываем. Материалы доступны и напрямую из GitHub:

- [Скачать офлайн-тестбук REV43, HTML (RU/EN)](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html)
- [Скачать тестовую APK REV43 — архив GitHub Actions](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391/artifacts/13060555171)
- [Инструкция тестировщика RU](docs/testing/INSIDERS-FIELD-TEST-REV43-RU.md) · [EN](docs/testing/INSIDERS-FIELD-TEST-REV43-EN.md) · [Статус](docs/DEVELOPMENT_STATUS.md)

[REV44 CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41250348806) прошла сборку/API26/API34; интеграционный [CI 41342607390](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342607390) также успешен, но повторный [CI 41342739222](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41342739222) выявил два падения API26 из-за оставшейся открытой клавиатуры. [Текущий аудит REV45](docs/AUDIT-REV45-2026-11-01-RU.md) содержит 49 предупреждений lint, причины и ограничения. Подтверждённых аппаратных PASS REV43 пока нет. Ссылки ниже на релиз NekoFlash 6.0.0-alpha11 относятся к старой версии, не к новой бете.

---

<details>
<summary>Архив REV5 — старый снимок, не текущее состояние ветки main</summary>

## Историческая контрольная точка REV5 (архив)

> **Статус:** проект в разработке. Это полный Android-исходник оригинального NekoFlash 6.0.0-alpha11 с первым переносом согласованного экрана **Sideload REV3**. Это **не NekoFlash Pro** и **не готовый APK NekoFlash 2.0**. Остальной утверждённый дизайн REV2/REV4 ещё не перенесён в Android-код. Не выполняйте разблокировку без отдельного разрешения владельца устройства.

- [Аудит и дорожная карта REV5](docs/source-audit/NEKOFLASH2-REV5-AUDIT-AND-ROADMAP-RU.md)
- [Утверждённый дизайн и контрольные решения](docs/design-approved/README.md)
- [Детали исходной точки и локальные проверки](docs/source-audit/README-FIRST-RU.md)

**CI:** push и Pull Request проверяют lint + Debug APK; подписанный Release в workflow сборки запускается вручную с настроенными secrets. Выпуск по тегу остаётся отдельным workflow. Отсутствие release-ключей не должно делать первоначальный push красным.

---

</details>

**NekoFlash** — это инструмент для Android USB OTG, предназначенный для управления, обслуживания и прошивки одного Android-устройства напрямую с телефона или планшета — **без ПК** и без root-прав на хост-устройстве.

Приложение реализует **ADB и Fastboot напрямую через Android USB Host** и поддерживает прошивку recovery/boot образов, ADB Sideload, диагностику устройств и процессы Xiaomi Mi Unlock.

### Что вы можете делать

- Подключать устройства **Android к Android по USB OTG / USB-C**.
- Использовать **ADB** shell, push/pull, установку APK, команды перезагрузки и ADB Sideload.
- Использовать **Fastboot** для диагностики, поиска разделов, прошивки, загрузки в память (boot), очистки (erase), форматирования, переключения слотов и поддерживаемых OEM-команд.
- Прошивать `recovery`, `boot`, `init_boot`, `vendor_boot`, `dtbo`, `vbmeta` или другой выбранный раздел с помощью **Быстрой прошивки**.
- Прошивать совместимые кастомные recovery, такие как **TWRP**, прямо со смартфона Android.
- Выполнять поддерживаемые сценарии **Xiaomi Mi Unlock** через Fastboot по USB.
- Работать без десктопного `adb`, `fastboot` и компьютеров с Windows/Linux/macOS.

> [!WARNING]
> Прошивка, очистка, форматирование, переключение слотов и разблокировка загрузчика могут привести к потере данных или сделать устройство неработоспособным. Всегда проверяйте целевое устройство, образ, раздел и слот перед операциями записи.

## Скачать

Последний выпуск: **v6.0.0-alpha11**

- [NekoFlash v6.0.0-alpha11 APK](https://github.com/Ncorror/NekoFlash/releases/download/v6.0.0-alpha11/NekoFlash-6.0.0-alpha11.apk)
- [APK SHA-256](https://github.com/Ncorror/NekoFlash/releases/download/v6.0.0-alpha11/NekoFlash-6.0.0-alpha11.apk.sha256)

## Установка

1. Скачайте последний APK NekoFlash из раздела выше.
2. Проверьте контрольную сумму SHA-256 (по желанию).
3. Разрешите установку из неизвестных источников в настройках Android при запросе.
4. Установите NekoFlash на смартфон или планшет, который будет USB-хостом.
5. Подключите целевое Android-устройство кабелем USB OTG / USB-C.
6. Выберите режим: **ADB**, **Recovery / ADB Sideload** или **Fastboot**.

## Скриншоты

| Приветствие | Устройство / Главная | Быстрая прошивка |
|---|---|---|
| <img src="docs/screenshots/welcome.png" width="220" alt="Экран приветствия NekoFlash"> | <img src="docs/screenshots/home.png" width="220" alt="Главный экран NekoFlash"> | <img src="docs/screenshots/quick-flash.png" width="220" alt="Интерфейс быстрой прошивки"> |

| ADB Sideload | Терминал и логи | Xiaomi Mi Unlock |
|---|---|---|
| <img src="docs/screenshots/sideload.png" width="220" alt="Интерфейс ADB Sideload"> | <img src="docs/screenshots/terminal.png" width="220" alt="Встроенный терминал"> | <img src="docs/screenshots/unlock.png" width="220" alt="Интерфейс Mi Unlock"> |

## Лицензия и независимость

NekoFlash распространяется под лицензией Apache License 2.0.

NekoFlash — независимый проект, не связанный и не одобренный Google, Xiaomi, MiForge, Termux или другими упоминаемыми проектами.
