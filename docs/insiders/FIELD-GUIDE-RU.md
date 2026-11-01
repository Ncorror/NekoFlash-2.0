# Полевые тесты NekoFlash 2.0 · Insiders REV45

Распакуй весь комплект. Начни с START-HERE.txt. В BUILD.txt записаны коммит, запуск GitHub Actions, имя APK и SHA-256 именно этого файла. Открой testbook.html в браузере на HOST. Свой прогресс можно выгрузить в JSON и восстановить позже.

[Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy)

## Подготовка

**HOST** — телефон, на котором работает NekoFlash; **TARGET** — подключаемый телефон. Заряд обоих устройств, исправный кабель данных/OTG, разрешение USB в Android. Для ADB включи отладку на TARGET и подтверди RSA. Для Fastboot используй запасной TARGET, уже разблокированный по требованиям сценария.

На первом раунде **не запускай** `erase`, `format`, `flash`, `unlock`, `wipe-super`, Fastboot DATA/download или запись реальных разделов. Ими занимается отдельный согласованный лабораторный этап после резервной копии, проверки точной разметки и возможности восстановления. Во время реальной записи прошивки не отключай OTG.

## Три раунда

1. **Сегодня — безопасная диагностика.** Установка и версия APK; UI и масштаб шрифта, поворот с Gboard; открытие/закрытие терминала; русские символы/emoji; копирование вывода; USB permission/USB роли; безвредные `adb devices`, `adb shell getprop ro.product.model`, `adb shell id`; чтение `fastboot getvar` на запасном TARGET. Воспроизвести <https://github.com/Ncorror/NekoFlash-2.0/issues/20> при возможности.
2. **Расширенный HOST → TARGET.** Одноразовые `adb push/pull`, хеши, каталог из 512 файлов (`adb push -r` REV43), Stop посреди передачи, USB detach и reconnect **только во время ADB**, проверка неполных файлов, замеры памяти/длительности. Если у TARGET нет старого `maxdata`, пометь тест REV42 **BLOCKED**, не выдумывай результат.
3. **Лаборатория — только по отдельному разрешению.** Реальные Fastboot download/flash/format/sideload, работа со слотами и ошибки транспорта. Без подготовленного восстановления и разрешения — NOT TESTED/BLOCKED.

## Базовые команды (не выполнять разрушительных операций)

В терминале NekoFlash, когда TARGET подключён и авторизован:

```text
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.release
adb shell id
adb shell echo NEKOFLASH_TEST_OK
```

Тестовые файлы создаются **в Termux на HOST**:

```bash
termux-setup-storage
mkdir -p ~/storage/downloads/NekoFlashQA/tree
dd if=/dev/urandom of=~/storage/downloads/NekoFlashQA/probe.bin bs=1M count=16
for i in $(seq 1 512); do
  d="part-$((i/32))"
  mkdir -p ~/storage/downloads/NekoFlashQA/tree/"$d"
  printf 'probe-%s\n' "$i" > ~/storage/downloads/NekoFlashQA/tree/"$d"/"file-$i.txt"
done
sha256sum ~/storage/downloads/NekoFlashQA/probe.bin
```

В терминале **NekoFlash**:

```text
adb shell mkdir -p /sdcard/Download/NekoFlashQA
adb push /sdcard/Download/NekoFlashQA/probe.bin /sdcard/Download/NekoFlashQA/probe.bin
adb pull /sdcard/Download/NekoFlashQA/probe.bin /sdcard/Download/NekoFlashQA/probe-return.bin
adb push /sdcard/Download/NekoFlashQA/tree /sdcard/Download/NekoFlashQA/
adb shell find /sdcard/Download/NekoFlashQA/tree -type f
```

Сравни SHA-256 исходного и возвращённого файла **в Termux HOST**. Для Stop/отключения используй только тестовые файлы и зафиксируй момент действия. Не удаляй пользовательские данные TARGET.

## Как сдать отчёт

В HTML укажи обе модели/Android, USB-кабель, точный build ID, статус **каждого** выполненного теста, воспроизведение ошибки, скриншоты и предложения. Подключи `/Download/NekoFlash/logs` кнопкой выбора папки; HTML **не может** читать её автоматически без твоего разрешения. Если браузер выдаёт только разовый снимок папки, перед ZIP ещё раз нажми обновление.

ZIP включает `report.txt`, `progress.json`, `evidence-manifest.json`, исходные `log-*`, `trace-*.bin`, `session-summary-*` и выбранные фото/видео. **Байты логов не очищаются, не маскируются и не сокращаются**. Экспорт использует ZIP32; при архиве больше ~4 ГиБ он выдаст ошибку, а исходные файлы останутся на месте. Для больших сессий разделяй архивы, не удаляй оригиналы.

Отправляй готовый ZIP в **доверенный личный чат/закрытую тестовую группу** как документ и кратко напиши «HOST, TARGET, build, симптом, шаги воспроизведения, время». Не размещай сырые логи, серийные номера, RSA, токены Mi или другие секреты в публичных GitHub Issues. **Тестбук ничего сам не отправляет в сеть**.
