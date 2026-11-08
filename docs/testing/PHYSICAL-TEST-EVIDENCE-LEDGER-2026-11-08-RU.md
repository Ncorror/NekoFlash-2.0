# NekoFlash 2.0 — единый реестр реально выполненных проверок

**Срез:** 8 ноября 2026, исходники `main` на коммите [`6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e), PR #62 уже слит. **Это реестр свидетельств, а не новый список заданий.** [EN summary](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md).

## Правила статусов

- **FIELD PASS:** выполнено на физическом устройстве, с указанием точной сборки и реального результата. Один аппарат/прошивка/USB-кабель не подтверждает совместимость со всеми остальными.
- **CI PASS:** автоматическая проверка на конкретном SHA. Не означает аппаратного прохождения.
- **FIELD FAIL / REPRODUCED:** реальный дефект был пойман; последующее исправление не стирает свидетельство отказа.
- **VERIFY_PENDING / PARTIAL:** передача могла завершиться, но реальный итог установки/записи независимо не подтверждён. Не считать ни PASS, ни FAIL.
- **NOT VERIFIED:** опубликован код, прототип или инструкция, но аппаратный результат для конкретной новой ревизии отсутствует. **BLOCKED** — нет требуемого безопасного стенда/образов/бэкапа.
- Никогда не переносить статусы из старого TestBook в новую физическую кампанию автоматически. Исторический PASS сохраняется, новая проверка назначается только ради изменённого кода, нового устройства или отсутствующего свидетельства.

## Испытанные физические стенды — не смешивать

| Стенд | Образ устройства TARGET | Ключевые исходные отчёты |
| --- | --- | --- |
| H1 | POCO F7 / onyx Android 16 HOST → POCO X7 Pro / rodin, разблокированный TARGET (старые alpha10) | [rodin alpha10](../evidence/POCO_X7_PRO_ALPHA10.md) |
| H2 | POCO F7 / onyx Android 16 HOST → **разблокированный** POCO X3 Pro / vayu, Android 14 / Evolution X | [vayu alpha10.1](../evidence/POCO_X3_PRO_ALPHA10_1.md), [7 ноября REV48](../insiders/FIELD-REPORT-2026-11-07-ONYX-VAYU-RU.md) |
| H3 | POCO F7 / onyx Android 16 HOST → **другой, заблокированный** POCO X3 Pro / vayu, Android 12 | [issue #57 — исходная ошибка, фиксы и протокол приёмки](https://github.com/Ncorror/NekoFlash-2.0/issues/57) |

Отчёты H2 и H3 нельзя объединять в один тест: **на H3 не прошивались разделы, не выполнялся unlock, wipe или destructive flash.**

## 1. H1 — POCO X7 Pro rodin, серия alpha10

| Реально выполнено | Итог и точная граница |
| --- | --- |
| ADB/RSA, `shell_v2`, reboot в bootloader, Fastboot handshake, unlock flow и чтение A/B | **FIELD PASS** на ранних alpha10-сборках; это не новый DEV REV55+ |
| Quick Flash: выбор active slot и all-slots, реальная запись **`vendor_boot_a` и `vendor_boot_b` по 64 МиБ** | **FIELD PASS** на `34ca8302eb59`; обе команды записи подтверждены Fastboot |
| Sideload Evolution X, большой пакет **3 889 931 331 байт**, закрытие до `DONEDONE` | **Классификация PASS, результат транспорта `VERIFY_PENDING`**, сам статус не доказывает установку |
| Финальная загрузка Evolution X Android 16 после цикла | **FIELD PASS** по экрану устройства, без публикации приватных фото |
| ADB shell H13: исходный отказ после первого потока → исправленная сборка `3d3e85e5fce1`, 5/5 команд, exit 0 | **REPRODUCED → FIELD PASS** только для зафиксированной сборки; ранний FAIL сохранён |

Источник: [полный отчёт rodin](../evidence/POCO_X7_PRO_ALPHA10.md).

## 2. H2 — POCO X3 Pro vayu, **разблокирован**, Android 14

| Реально выполнено | Итог и сборка |
| --- | --- |
| ADB DEVICE/RSA/`shell_v2`, повторные команды, reconnect и финальный marker | **FIELD PASS**, `6.0.0-alpha10+de1225b9cd5b.31373308470` |
| Bootloader Fastboot и определение A-only | **FIELD PASS** на alpha10.1 и повторно REV48 |
| Настоящий `recovery.img` размером **128 МиБ**, Fastboot DATA, `flash:recovery OKAY`, переход и загрузка Recovery | **FIELD PASS по протоколу и запуску Recovery** на alpha10.1 и повторно **7 ноября на REV48** (`79c8c0a9d7ac`). Независимого readback раздела в REV48 не было |
| Firmware Sideload ~103 МиБ → Recovery `operation_end status=0` | **FIELD PASS**, alpha10.1 |
| Evolution X Sideload ~2,18 ГиБ → Recovery подтвердил успех, один post-result stream-close log | **FIELD PASS с замечанием о логировании**, alpha10.1 |
| DerpXtreme Sideload ~14,6 МиБ: peer закрыт до `DONEDONE`, затем Recovery `operation_end status=0` | **FIELD PASS**, alpha10.1, поскольку есть независимое подтверждение |
| Fastbootd и итоговая загрузка Evolution X / Android 14 | **FIELD PASS**, alpha10.1 |
| Отдельный `OrangeFox-R11.3-vayu.zip` **73 757 208 байт**, REV48 7 ноября: передача почти 100%, `DONEDONE` нет, проверка Recovery не связала attempt | **VERIFY_PENDING, НЕ подтверждённый install PASS**; сам прошитый Recovery запускался и позволял перейти в Fastbootd |
| `getprop`, `id`, `echo NEKOFLASH_TEST_OK` после загрузки Android 14 | **FIELD PASS**, REV48, raw stdout/exit 0 |
| Operation Center: переход `Выполняется` → `Нужна проверка`, прогресс и кнопки видны | **PARTIAL UI PASS** на REV48; поведение после force-stop, rotation и закрытия старого результата не полностью принято |

Источники: [alpha10.1](../evidence/POCO_X3_PRO_ALPHA10_1.md), [REV48 физический отчёт](../insiders/FIELD-REPORT-2026-11-07-ONYX-VAYU-RU.md), [открытая issue #47](https://github.com/Ncorror/NekoFlash-2.0/issues/47).

### H2 — диагностические регрессии REV51/52/53, 7 ноября

| Сборка и испытание | Итог |
| --- | --- |
| REV51 `18afb2c45cf6`, ZIP `1794056370360` — 55/55 файлов с корректными SHA-256/размерами | **FIELD PASS по целостности имеющихся байтов**, но манифест **INCOMPLETE_OR_UNVERIFIED**: старые прологи REV48 отсутствуют, два JSON изменились во время копирования. Это **НЕ** доказательство полного журнала |
| REV52 `cbe6cc87942f`, ZIP `1794064372351` — 103/103 файла CRC/SHA-256; `ATOMIC_SUMMARY_REPUBLISHED` | **FIELD PASS по целостности архива и исправлению ложного SOURCE_CHANGED**, исторический пропуск прологов REV48 сохраняется |
| REV52 короткий `adb shell echo REV52_SHELL_OK` — **3/3 FAIL**; `REV52_REPEAT_OK` — **6/7 FAIL**, хотя `id` и PTY работали | **FIELD FAIL воспроизведён**, причина выявлена: ранний CLSE при `CLOSE_STDIN`. Не списывать как USB-обрыв |
| REV53 `b44792605c98`, 10 отдельных `adb shell echo REV53_SHORT_OK` | **10/10 FIELD PASS**, все stdout 15 байт, stderr 0, exit 0; **8/10** получили ранний CLSE, восстановленный из буфера. ZIP **35/35 SHA PASS** |
| REV53 после INFO-полировки `c48f8eae8009`, две авторизованные команды | **2/2 FIELD PASS** на том же H2; ранний CLSE остаётся в raw journal как INFO, без ложного GUI warning, ZIP **46/46 SHA PASS**. Один вызов без USB был корректно отклонён и не является FAIL авторизованного ADB |
| PR #61: инструмент `verify_diagnostic_zip.py --check-source-inventory` обнаруживает отсутствующие в ZIP исходные файлы | **CI/source-test PASS**, **нового физического сравнения всех доступных source roots ещё нет**; не выдавать за восстановление пропавших исторических событий |

Источники: [issue #50](https://github.com/Ncorror/NekoFlash-2.0/issues/50), [issue #52](https://github.com/Ncorror/NekoFlash-2.0/issues/52), [REV53 memo](../source-audit/REV53-SHELLV2-EARLY-CLOSE-2026-11-07-RU.md).

## 3. H3 — другой POCO X3 Pro vayu, **заблокирован**, Android 12, ночь 7–8 ноября

| Тест / сборка | Реальный результат |
| --- | --- |
| REV53 merged `b82af9ffa114`: USB/RSA/ADB, 16 МиБ push→pull | **FIELD PASS** |
| Изначальный рекурсивный Push 512 файлов | **FIELD FAIL: 223/512**. Файл №224 (1016 байт полезных данных +8 SYNC header =1024 USB WRTE) не получил ACK. Не записывать первоначальный прогон как PASS |
| Изолированный 1016-байтный файл | **3/3 FIELD FAIL**, одинаковая граница 1024; 1015/1017 (1023/1025 на проводе) PASS. Один эксперимент имел физический обрыв кабеля, но были независимые воспроизведения |
| REV54 кандидат `6cb2233fcdc7`, CI [#494](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42418799676): 1015/1016/1017 байт | **FIELD PASS**, каждый target SHA-256 равен HOST; ошибка no-ACK устранена для данной связки |
| REV54 новая директория, рекурсивный Push и Pull | **512/512 FIELD PASS** с полной проверкой состава, размеров и SHA-256; прежние 223 файла не удалены |
| REV54 новый 16 МиБ файл Push/Pull, TARGET SHA-256 и HOST `cmp` | **FIELD PASS**, 16 777 216 байт туда/обратно |
| Промежуточная проверка каталога до запуска Push показала 0 файлов | **ОШИБКА ПОРЯДКА ПРОВЕРКИ, НЕ транспортный FAIL**; затем выполнен настоящий полный прогон |
| PR #58/#59: ADB ZLP фикс в main и тестовый API26 fontScale fixture, post-merge CI [#502](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327) | **CI PASS**, issue [#57](https://github.com/Ncorror/NekoFlash-2.0/issues/57) закрыта **для H3**. Это не аппаратная прошивка, не тест всех USB-адаптеров |

Источник: [13 комментариев с последовательностью FAIL→FIX→PASS](https://github.com/Ncorror/NekoFlash-2.0/issues/57).

## 4. Физические UX/хранилище/отчёты — найденные проблемы

| Сценарий | Свидетельство | Что неизвестно |
| --- | --- | --- |
| REV29 терминал при повороте/IME и сворачивании | **FIELD FAIL**: появлялось дополнительное окно ввода и задержка анимации; [#20](https://github.com/Ncorror/NekoFlash-2.0/issues/20) | Исправления REV34–49 прошли CI, но **новой физической приёмки конкретного REV49 UI нет** |
| REV48 терминал после поворота/команды | **FIELD FAIL**, пустой/сдвинутый вывод, заголовок; поправлен код REV49 | Не считать REV49 на телефоне PASS на основании CI |
| REV48/49 TestBook в Android Chrome через `content://` | **FIELD FAIL**: копирование команд и report.txt, сохранение JSON/ZIP не работали. APK отдельно успешно экспортировал raw ZIP | REV50 добавил ручные fallback/Share/Save, но **физический PASS Chrome JSON+ZIP и восстановления прогресса не предъявлен**, [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49) открыта |
| REV48 Operation Center `VERIFY_PENDING 100%` после force-stop | **FIELD FAIL** на старой сборке; код REV47/49 исправлялся | Строгий физический повтор на исправленной версии пока не найден; [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47) открыта. Дизайн центра отдельно **отложен** ([#46](https://github.com/Ncorror/NekoFlash-2.0/issues/46)) |
| POCO F7 Android DocumentsUI: пустой корень, но работающий Downloads | **FIELD intermittent**; позднее те же файлы появлялись даже в Quick Flash picker без доказанного изменения кода | Причина не выяснена, [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48) открыта |
| Стресс по памяти на длинном shell stdout/stderr и крупных деревьях | Источник анализировался, реальных полных измерений OOM/памяти не предъявлено | [#19](https://github.com/Ncorror/NekoFlash-2.0/issues/19) и [#36](https://github.com/Ncorror/NekoFlash-2.0/issues/36) остаются предметом отдельных performance/retention-проверок |

## 5. Что уже подтверждено только автоматикой

- **REV55 кандидата:** CI [#501](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42426015449): 336 JVM, lint 0, API26 28/28 (и повтор на идентичном SHA), API34 28/28, комплект PASS. Интеграция CI [#502](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327) PASS. Историческая ошибка нестабильности тестов [#16](https://github.com/Ncorror/NekoFlash-2.0/issues/16) шире одного `fontScale`, issue остаётся открыта.
- **PR #60/#61:** документационная коррекция и проверка состава доступных source log-файлов: CI PASS, [post-merge run](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42513570788) SUCCESS.
- **PR #62:** новый список 43 сценариев с `LAB-01…LAB-06` без изменения UI/Storage/Export TestBook, [CI кандидата](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42515445165) PASS, merge в `main` [`6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e). **Это проверка перечня заданий, НЕ аппаратное исполнение 43 сценариев и НЕ новая прошивка.** После merge GitHub запустил [новый main CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42517047929); его итог нужно проверять отдельно.

## 6. Не назначать заново без нового основания

- Не повторять как «непроведённые»: H3 1023/1024/1025, H3 512-file push/pull, H3 16 МиБ, H2 REV53 10+2 коротких shell, H2 REV48 реальную запись 128 МиБ Recovery, H1 два `vendor_boot`, H2 alpha10.1 три подтверждённые Recovery-пакета.
- Если требуется именно регрессия **последнего SHA**, другой TARGET, новая прошивка, другая топология, либо полный протокол восстановления — это **новый отдельный тест** с указанием изменившегося условия. Не стирать прежний PASS.
- `LAB-01…LAB-06` в TestBook — **планы, а не исполненные тесты**; некоторые их операции уже испытаны в старых alpha10/REV48-сборках. План новой лаборатории не является основанием автоматически прошивать рабочее устройство.
- Мы **не выполняли** в текущем REV54/55 раунде подтверждённый новый Mass Flash, `userdata wipe/format` и полный контролируемый detach **во время Fastboot DATA** (и намеренно не должны). Открытый пункт для новой кампании должен называться точно и иметь критерий, отличающий его от старого опыта.

## Правила для TestBook и следующей кампании

**Рабочий план тестов строить от этого реестра, а не от оставшихся 43 исторических карточек.** Не удалять TestBook или JSON прошлых отчётов без отдельного решения автора. Нынешний HTML — инструмент отметок, но не самостоятельный источник того, что тест ранее НЕ выполнен. Следующий список тестов согласовать в чате, исключив уже доказанные однотипные проверки. Сырые ZIP/USB trace/уникальные серийники/ключи остаются в доверенном приватном канале и не попадают в публичный репозиторий.
