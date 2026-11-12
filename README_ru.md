<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

**Релизная линия:** `6.0.0-alpha11` · versionCode `234`.

NekoFlash позволяет с одного Android-телефона работать со вторым по USB OTG через ADB, Fastboot и Recovery. Для хоста нужны Android 8.0+, USB Host и кабель с передачей данных. Root на хосте необязателен.

Русский · [English](README.md)

## Статус релиза

Финальная аппаратная кампания alpha11 закрыта для выпуска:

- **Fastboot format/wipe (#64): FIELD PASS.** На разблокированном POCO X3 Pro `vayu` подтверждены `fastboot format userdata`, обработка metadata/cache, `fastboot -w`, reboot и последующая загрузка Android до мастера первоначальной настройки.
- **Большой ADB Pull (#72): закрыт.** POCO X3 Pro host → POCO X3 Pro target прошёл Pull `framework.jar`, повторные Pull/Push 128 МиБ и сравнение SHA-256. POCO F7 / `onyx` выделен как отдельный плавающий host-side USB receive case; большой Pull теперь пишет устойчивые checkpoint по 256 КиБ и публикует итоговый файл только после совпадения SHA-256.
- Recovery/Sideload, Fastboot handshake/reboot, Mass Flash, вход Xiaomi, выбор/очистка ZIP и терминал/поворот имеют реальный аппаратный PASS на протестированных устройствах и сборках.

Актуальная сводка: [Hardware validation](docs/HARDWARE_VALIDATION.md). Датированные низкоуровневые эксперименты оставлены только в [журнале аппаратных доказательств](docs/testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md).

## POCO F7 / onyx как host

POCO F7 (`onyx`, Android 16) неоднократно показывал плавающие ошибки входящего USB ещё в Legacy NekoFlash. На том же target и тех же файлах POCO X3 Pro в роли host работает нормально. Поэтому NekoFlash сохраняет fail-closed транспорт и после ручного переподключения продолжает большой Pull только с полностью записанного checkpoint.

Чувствительность питания/VBUS правдоподобна по полевому поведению, но напряжение и ток мы не измеряли, поэтому это **не объявляется доказанной первопричиной**. Короткий кабель или USB-хаб с внешним питанием можно использовать как диагностический вариант для слабой/нестабильной OTG-шины.

## Скачать

- [GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases) — официальные подписанные релизы.
- [Зелёные CI main](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) — debug/test APK точного коммита.
- [Telegram Insiders](https://t.me/+NR_FiUmvXlViOTUy) — координация тестировщиков.

Старые Insiders REV ZIP не считать текущим production-артефактом.

## Возможности

- ADB shell, push/pull, установка APK и диагностика.
- Quick Flash, Mass Flash и Fastboot tools.
- Recovery ADB Sideload: выбор файла отдельно, запуск отдельно.
- Xiaomi Unlock account/session workflow.
- Operation Center, полный diagnostic export и raw USB evidence.
- Генерация ext4/F2FS на host для поддерживаемых Fastboot format.
- Большой Pull с checkpoint/resume и финальной проверкой SHA-256.

## Безопасность и диагностика

NekoFlash не повторяет молча неудачные разрушительные Fastboot-команды и повреждённые ADB-пакеты. Разрушающие действия остаются явными. Полный diagnostic export сохраняется для технического разбора; сырые журналы лучше передавать только через доверенный канал.

См. [USB troubleshooting](docs/USB_CONNECTION_TROUBLESHOOTING.md), [Privacy](PRIVACY.md) и [Third-party notices](THIRD_PARTY_NOTICES.md).

## Сборка и релиз

CI использует JDK 17, проверенный Gradle Wrapper и GitHub Actions, закреплённые по commit SHA. Production-сборка fail-closed: защищённый release workflow требует постоянный ключ, сверяет SHA-256 сертификата `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`, собирает APK, повторно проверяет сертификат APK и публикует checksum/build metadata.

Релизный tag обязан совпадать с `versionName` и указывать на одобренный `main`. См. [release readiness](docs/RELEASE_READINESS-2026-11-10.md).

## Проект

Автор и разработчик — [Ncorror](https://github.com/Ncorror). Участник проекта — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork](ASSETS_LICENSE.md) · [Changelog](CHANGELOG.md)
