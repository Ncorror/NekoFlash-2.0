# NekoFlash 2.0 — полный аудит REV14

Дата: 2026-10-12  
База аудита: PR #2, ветка `feature/rev11-core-cleanup`, после REV13.  
Статус: аудит исходников + исправления, **не** подтверждение реальной прошивки.

## Итог

На момент аудита не найден P0-дефект, который очевидно разрешает повторную запись после неоднозначного USB-сбоя или превращает UNKNOWN в SUCCESS. Критические транспортные инварианты Fastboot/Native USBFS и Recovery verification сохранены.

До большого аппаратного теста остаются P1-долги, перечисленные ниже.

## Объём и архитектура

- `MainActivity.kt`: ~4343 строки — всё ещё God-object: навигация, USB UI, терминал, файловые операции, Fastboot UI, настройки, отчёты, Mi Unlock.
- `DeviceViewModel.kt`: ~1894 строки — USB lifecycle, операции, логирование, Flash/Sideload/Unlock orchestration.
- `AdbProtocol.kt`: ~2806 строк.
- `FastbootProtocol.kt`: ~2481 строк.
- Декомпозиция начата (`ConsoleDockController`, `TabController`, `FirmwareSource`, `WorkspaceImportNaming`), но ещё не завершена.

### REV14 исправляет

- Welcome теперь содержит только реальный action доступа к файлам и вход. Старые скрытые checkbox/notification/battery/status controls и persisted risk-pref удаляются.
- `OnboardingGate` остаётся только process/task gate, чтобы USB attach не мог обойти Welcome. Разрешение памяти **не** блокирует вход.
- Удаляются искусственные ограничения `MAX_URI_LENGTH=4096` и `MAX_DISPLAY_NAME_LENGTH=256` в draft очереди. Имена/URI не обрезаются молча.
- GUI manual partition больше не запрещает верхний регистр и `:` только из-за локального regex.
- Удаляется неиспользуемый `maxSegmentsPerStream` из DiagnosticLogStore.
- DEV suffix становится постоянным `-dev`, а не устаревшим `-rev6-dev`.
- Release workflow обязан запускать structural/i18n и JVM regression tests до подписания и публикации.

## P1 — обязательно до merge

### 1. Интерактивный ADB PTY

Сейчас `adb shell` действительно открывает `shell,v2,pty:`, но ввод пользователя передаётся **построчно**: `sendInteractiveShellInput(line)` добавляет `\n`. Требование посимвольного ввода, paste и корректного backspace через IME пока не выполнено полностью.

План: отдельный PTY input widget/InputConnection, raw UTF-8 commit, DEL, Enter, Ctrl-C/Ctrl-D; HOST CLI остаётся обычным line editor.

### 2. RU/EN

Ресурсы RU/EN имеют одинаковые ключи и placeholders, но старые сообщения в `MainActivity`, `DeviceViewModel` и protocol logs ещё встречаются literal-строками. Они видны в Console/диалогах и должны постепенно перейти в ресурсы.

Новые возможности с REV11 обязаны добавлять сразу оба языка. Технические команды/URI/partition IDs не переводятся.

### 3. Большие Fastboot-образы

Одиночная команда Fastboot использует стандартный `download:%08x`: размер кодируется 32 битами. Удалить проверку >4 GiB без нового протокола нельзя.

Также host обязан учитывать `max-download-size`. Для больших Android sparse-образов нужен корректный sparse/chunk splitting, а не сообщение «-S не требуется». До реализации не заявлять полную совместимость больших образов.

### 4. Mass Flash + SAF

Quick Flash и Sideload получили прямой seekable SAF. Массовая очередь пока хранит локальные `file://` записи. Нужны persisted `content://` URI grants, восстановление после process death и проверка seek/size непосредственно перед каждой операцией.

### 5. Аппаратный Native USBFS FD test

REV13 компилируется и JNI совпадает, но `dup(fd) -> fstat -> pread -> URB` ещё не прошёл аппаратную передачу с Android DocumentsProvider. Это блокер заявления о готовности.

## Реальные ограничения, которые НЕ надо удалять

- Fastboot wire command <= 64 bytes — проверяется транспортом.
- `download:%08x` не представляет payload >= 2^32 bytes.
- Единственная активная USB mutation и transaction lock.
- Native USBFS drain/poison после отмены/ошибки; никакого blind retry в той же DATA-сессии.
- Проверка read access/size и точной цели операции.
- Recovery UNKNOWN не становится SUCCESS.
- Release APK выпускается только с постоянным проверенным сертификатом.

## Файлы и доступ к памяти

Пути утверждены и не меняются:

- package/namespace: `ru.forum.adbfastboottool`
- source tree: `app/src/main/java/ru/forum/adbfastboottool/`
- workspace: `/sdcard/Download/NekoFlash`

`MANAGE_EXTERNAL_STORAGE` нужен только для legacy workspace File-пути. Direct SAF для Sideload/Quick Flash работает без all-files и получает доступ только к выбранному пользователем документу.

## UI

REV10 устранил обнаруженные на телефоне перенос кнопки терминала, слабый контраст вкладок и лишние элементы Welcome. REV14 физически удаляет скрытый legacy-tail Welcome вместо сохранения invisible controls.

Повторная проверка обязательна на реальном экране: Welcome, нижние 5 вкладок, Quick Flash, Sideload и fullscreen Terminal с IME.

## Логи и приватность

- Исторические сегменты не удаляются автоматически.
- Compact/trace разделены.
- Share/report sanitizer маскирует serial/account/device IDs, приватные пути и длинные идентификаторы.
- Mi Account логи показывают имена cookies, не их значения.
- Секреты signing key не находятся в репозитории; CI получает их через GitHub Secrets.

## CI/release

До REV14 обычный build прогонял structural/i18n/JVM/lint/APK, но tag Release workflow не повторял structural/JVM regression gates. REV14 исправляет это: release не может подписаться и публиковаться без тех же проверок.

## P2 / последующая архитектурная уборка

- Вынос USB presentation, file/source picker, Fastboot UI, reports/history, settings и Mi Unlock из `MainActivity`.
- Разделение orchestration в `DeviceViewModel`.
- Удаление оставшихся compatibility views/handlers только после поиска call-sites.
- Перевод protocol/log strings.
- Удаление deprecated compatibility shims после одного стабильного hardware cycle.

## Gate большого теста

До destructive test должны быть зелёными: structural REV14, i18n, JVM, Android lint, Debug APK, плюс безопасный device smoke: Welcome/permission, RU/EN, USB enumerate, ADB connect, Fastboot getvar, SAF select без записи, terminal/IME.

Настоящий flash/sideload/unplug/cancel DATA — только на выделенном разблокированном устройстве с совместимыми тестовыми файлами и планом восстановления.
