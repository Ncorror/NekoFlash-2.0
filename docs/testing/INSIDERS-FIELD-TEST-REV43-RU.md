# NekoFlash 2.0 — полевой тестбук Insiders REV43

**Telegram-группа тестирования: [NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy).** Там размещаем APK, тестбук и отчёты; ZIP с исходными логами отправляй только в доверенную группу или лично ответственному тестировщику. Прямые ссылки GitHub также доступны ниже.

**Статус 1 ноября 2026:** старт тестовой кампании на реальных Android HOST → TARGET; **новых подтверждённых результатов REV43 на физических устройствах пока нет**. Это инструкция, не протокол пройденных испытаний. Тестируем только **NekoFlash 2.0**, не NekoFlash Pro.

## Скачать тестбук и приложение для NekoFlash Insiders

- [Тестбук REV43 — один автономный HTML](../insiders/testbook.html). Его можно открыть в браузере Android без интернета после скачивания; RU/EN, чекбоксы, PASS/FAIL/BLOCKED/NOT TESTED, сохранение прогресса, JSON, ZIP.
- [Скачать тот же HTML непосредственно из ветки main](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html). GitHub Actions тестовой ветки дополнительно публикует **NekoFlash-Insiders-Testbook-REV43-…** как отдельный скачиваемый artifact.
- [Debug APK из зелёного точного PR REV43](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391/artifacts/13060555171) — скачивание GitHub Actions требует доступ к artifact; извлечь APK из ZIP. Сборка NekoFlash `6.0.0-alpha11-dev`, `versionCode 234`, applicationId `ru.forum.adbfastboottool.dev`. **Не удаляй оригинальный NekoFlash**.
- Исходник REV43 PR: `27ab796d100387b3530bd88a6faf28e48d1c717c`. Объединённая DEV: `bd0d1fbf0ff5ba69634df5281fa87e2ff1289bf5`. Не подставляй SHA-256 старого APK — свой скачанный APK проверяй заново: `sha256sum /sdcard/Download/имя.apk`.

## Почему результаты CI не равны аппаратному PASS

| Проверка | Результат |
|---|---|
| PR REV43, [CI 41247325391](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391) | Сборка/JVM/lint, Android API 26 и 34 — PASS |
| Объединённый DEV, [CI 41248168147](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147) | Build PASS, API 34 PASS, **API 26 FAIL** |
| Сбой DEV API 26 | `thousandRowsRecycleAndKeepOrderFormAndScrollAcrossRecreation`, `largeFontAndLandscapeKeepNavigationAndMassActionVisible` — два `UI did not reach the expected state`, плюс недостающие UI-снимки |
| Физические Android HOST → TARGET REV43 | **Ожидаем доказательные отчёты тестировщиков**; PASS не заявлен |

Сохранены исторические испытания alpha10, но они не доказывают совместимость новой DEV.

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

## Что пока остаётся

- Реальные 30–120 минут ADB/PTY нагрузки, стабильность OTG/USB роли, старый ADB maxdata, Stop/reconnect и одновременные callbacks.
- RAM и отклик UI при `adb push -r` большого дерева, чтение логов без потерь.
- TERM-01 (клавиатура/поворот/закрытие); нестабильные два UI-теста в CI API 26 — <https://github.com/Ncorror/NekoFlash-2.0/issues/16>.
- Только после подтверждений и документации — решение о Release Candidate. `main` и защита релиза здесь не менялись.
