<img src="docs/artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="88" alt="NekoFlash">

# NekoFlash 2.0

NekoFlash позволяет использовать один Android-телефон как USB-host для второго и работать с ADB, Fastboot, Recovery/Sideload, диагностикой и Xiaomi account/unlock без ПК.

**Последний подписанный релиз:** [v6.0.0-alpha12](https://github.com/Ncorror/NekoFlash-2.0/releases/tag/v6.0.0-alpha12) · versionCode 235
**SHA-256 релизного APK:** `f5013cb895d11f7c42272075eb384e0b0bbc9d1fa3d537321504fbd01499e53b`

Русский · [English](README.md)

**Сайт проекта:** [https://nekoflash.ncorror.chatgpt.site/ru/](https://nekoflash.ncorror.chatgpt.site/ru/)

## Android без компьютера: инструкции NekoFlash

NekoFlash позволяет использовать **один Android-телефон для управления и прошивки другого без ПК** через USB OTG. Выбери инструкцию для нужного режима:

- [Fastboot без ПК — с одного Android-телефона на другой](https://nekoflash.ncorror.chatgpt.site/ru/guides/fastboot-from-android/) — определение устройства, проверка разделов и операции Fastboot.
- [Установка Recovery, TWRP или OrangeFox без компьютера](https://nekoflash.ncorror.chatgpt.site/ru/guides/flash-recovery-twrp/) — выбор совместимого образа и проверка схемы разделов.
- [ADB Sideload без ПК](https://nekoflash.ncorror.chatgpt.site/ru/guides/adb-sideload/) — отправка ZIP из второго телефона через режим Recovery.
- [ADB без компьютера: терминал, файлы и APK](https://nekoflash.ncorror.chatgpt.site/ru/help/#modes) — USB-отладка, авторизация ADB, Shell, Push/Pull и установка приложений.
- [Разблокировка загрузчика Xiaomi / Redmi / POCO](https://nekoflash.ncorror.chatgpt.site/ru/help/#unlock) — вход в Mi Account и обнаружение Fastboot проверены; **полная разблокировка в NekoFlash 2.0 пока ждёт повторного аппаратного теста**. Ограничения аккаунта и ожидание определяет Xiaomi.

**Официальная загрузка:** [подписанный APK NekoFlash 2.0 на GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases/latest). Актуальные исходники и релизы ведёт [Ncorror](https://github.com/Ncorror).


## Текущее состояние

Подписанный alpha12 опубликован: завершённый аудит, обновлённые настройки с GitHub/Telegram и проверенный release workflow с постоянным сертификатом. Аппаратные результаты относятся к подтверждённой кампании alpha11; alpha12 не заявляет отдельную новую серию физических тестов.

Основные аппаратные результаты:

- Fastboot format/wipe на разблокированном POCO X3 Pro `vayu`: `format userdata`, `fastboot -w`, metadata/cache, reboot и загрузка до первоначальной настройки Android — **FIELD PASS**.
- Контроль ADB на POCO X3 Pro host → POCO X3 Pro target: Pull `framework.jar` и повторные Pull/Push 128 МиБ с совпадающим SHA-256 — **FIELD PASS**.
- POCO F7 / `onyx` остаётся отдельным плавающим случаем USB-host receive. Большой Pull использует устойчивые checkpoint и итоговую проверку SHA-256; повреждённый USB-пакет не повторяется молча.
- Recovery/Sideload, Mass Flash, переходы Fastboot, Xiaomi sign-in, выбор/очистка ZIP и терминал/поворот имеют принятые аппаратные доказательства на протестированных связках.

См. [текущий статус](docs/STATUS.md) и [hardware validation](docs/HARDWARE_VALIDATION.md).

## Скачать

Для обычной установки используй [GitHub Releases](https://github.com/Ncorror/NekoFlash-2.0/releases/latest).

В публичном Release намеренно остаются только:
- APK;
- его SHA-256;
- `filesystem-tools-source.tar.gz` — обязательный corresponding source для встроенных filesystem tools.

Debug/test artifacts из Actions не являются production-релизом.

## Возможности

- ADB shell, install, push/pull и диагностика.
- Quick Flash, Mass Flash и прямые Fastboot-команды.
- Recovery ADB Sideload: выбор ZIP ничего не прошивает, запуск выполняется отдельным явным действием.
- Xiaomi account/session и unlock workflow.
- Operation Center и полный diagnostic evidence export.
- Host-side генерация ext4/F2FS для поддерживаемых Fastboot format.
- Большой Pull с checkpoint/resume и финальной сверкой SHA-256.

## Политика безопасности

NekoFlash — профессиональный инструмент. Сырые диагностические данные и прямой доступ к ADB/Fastboot сохраняются. При этом приложение не делает скрытый повтор неоднозначной разрушительной Fastboot-записи или повреждённого ADB-пакета.

Raw diagnostics могут содержать идентификаторы, команды и пути. Sanitized/shareable export создаётся отдельно и не меняет исходные журналы. См. [Privacy](PRIVACY.md), [Security](docs/SECURITY.md) и [Professional recovery policy](docs/PROFESSIONAL_RECOVERY_POLICY.md).

## Документация

Начинай с [docs/README.md](docs/README.md).

Главные документы:
- [Status](docs/STATUS.md)
- [Послерелизный аудит](docs/AUDIT_2026-11-13.md)
- [Hardware validation](docs/HARDWARE_VALIDATION.md)
- [USB troubleshooting](docs/USB_CONNECTION_TROUBLESHOOTING.md)
- [Repository/release security](docs/REPOSITORY_SECURITY.md)
- [Legal review](docs/LEGAL_REVIEW.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

Старые REV/audit-срезы сохраняются в Git history, а не лежат рядом как конкурирующие актуальные инструкции.

## Проект

Автор и разработчик — [Ncorror](https://github.com/Ncorror). Участник проекта — [IQ-HARRY7](https://github.com/IQ-HARRY7).

[Apache 2.0](LICENSE) · [Artwork terms](ASSETS_LICENSE.md) · [Changelog](CHANGELOG.md)
