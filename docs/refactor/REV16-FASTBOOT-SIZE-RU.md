# REV16 — реальные лимиты Fastboot и max-download-size

Цель REV16 — убрать неверное host-поведение без удаления реальных ограничений протокола.

## Что исправлено

- Перед `flash` и `download + command` проверяется размер выбранного payload.
- Сохраняется реальная граница одиночной команды `download:%08x`: один payload не может представить размер >= 2^32 байт.
- Если устройство сообщает `getvar:max-download-size`, NekoFlash учитывает **меньший лимит самого bootloader** ещё до DATA-фазы.
- Если `max-download-size` недоступен, NekoFlash не придумывает собственный меньший предел — bootloader остаётся источником истины.
- Параметр desktop-fastboot `-S/--sparse-limit` больше не игнорируется с сообщением «не требуется». Пока корректное sparse/chunk splitting не реализовано, такая команда явно не отправляется и пользователь получает сообщение RU/EN.

## Почему предел 4 ГиБ не «искусственный запрет»

Текущая wire-команда Fastboot содержит восемь hex-цифр размера: `download:%08x`. Удаление проверки позволило бы переполнение/неверную команду, а не поддержку больших образов. Для больших RAW/sparse нужен отдельный алгоритм разбиения на корректные sparse chunks с учётом `max-download-size`.

## Не изменено

- Native USBFS pipeline, drain/poison, no-blind-retry.
- SAF fd-путь REV13.
- Пути/package/workspace.
- Mass Flash порядок.
- `main` не трогаем до аппаратных проверок.

## Тесты

`FastbootDownloadSizePolicyTest` проверяет wire-limit, device-limit и отсутствие выдуманного device-limit. Structural REV16 проверяет, что preflight выполняется до `download`, а `-S` больше не проглатывается.

Аппаратный тест больших образов пока не проводить. Сначала безопасный getvar/small-image smoke на тестовом разблокированном устройстве.
