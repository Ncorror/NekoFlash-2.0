# NekoFlash 2.0 — сверка всех 43 карточек TestBook с уже полученными результатами

## Изменение статусов после физического раунда 9 ноября 2026

Смотри [дополнение к реестру H2](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md): на **разблокированном A-only vayu** под `6.0.0-alpha11+6a8accd8e33e.42517047929` уже выполнены операции, которые ниже, в снимке 8 ноября, названы невыполненными. Старую таблицу оставляем как **историческую на 8 ноября**; эти новые подтверждённые результаты имеют приоритет.

| Карточка | Уточнение 9 ноября | Что дальше |
| --- | --- | --- |
| `LAB-01` | Аппаратный preflight зафиксировал `vayu`, `unlocked=yes`, **A-only**; полноценная карточка общего допуска по всем условиям не аттестована | Отдельные подтверждённые свойства не превращать в общий PASS карточки |
| `LAB-03`, `FB-04` | Bootloader Fastboot и Fastbootd реально проверены, подтверждены `is-userspace` и финальные `OKAY` после DATA | Не повторять ради того же устройства и команды |
| `LAB-04` | **Mass Flash FIELD PASS 3/3:** `boot`, `dtbo`, `recovery`; реальные ответы Fastboot, Recovery затем загрузилось | Закрыть пробел «нет реального Mass Flash» для **этого** стенда/сборки. Итог загрузки Android отдельно не подтверждён |
| `LAB-05` | Lineage ZIP получил `Signature verification failed / error:21`, **исходный SHA не совпал**; OrangeFox ZIP, отправленный **из Lineage Recovery**, сменил Recovery, но exit-код инсталлятора не получен | **ROM INSTALL FAIL** с конкретным несоответствием SHA; OrangeFox переход наблюдался, INSTALL SUCCESS не утверждать. Не повторять по старому ZIP |
| `LAB-06` | `fastboot format userdata`: команда OKAY, Android не загружается; `fastboot -w`: **частичный destructive FAIL** на `erase:cache` после успешной записи userdata | Реальный тест **ВЫПОЛНЕН, исход FAIL**, нельзя снова писать «не выполнялся». [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64), [#65](https://github.com/Ncorror/NekoFlash-2.0/issues/65). Не повторять до диагностики причины |

**Разработческий регресс:** [PR #71](https://github.com/Ncorror/NekoFlash-2.0/pull/71) с исправленным безопасным выбором ZIP и наблюдением смены Recovery получил CI PASS (346 JVM, lint 0, API26/34). **На телефоне ещё не испытывался**, а `main` не менялся. Для новой APK будет отдельный короткий сценарий: выбор ZIP сам по себе не отправляет данные; старт только по подтверждению, сохранение файла для следующей операции, независимое свидетельство Recovery. Запускать повторную реальную прошивку только ради UI-аудита не требуется.

---


**Дата:** 8 ноября 2026; снимок списка из `docs/insiders/testbook.html` после PR #62 (`main` functional baseline [`6a8accd8e33e`](https://github.com/Ncorror/NekoFlash-2.0/commit/6a8accd8e33eef946efa1f18a829b0cc2b797e6e)). Основа: [полный журнал реальных испытаний](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md). **Эта таблица — аудит, НЕ результаты нового прохождения TestBook.** Старые галочки/JSON не переписывать и не переносить автоматически.

Легенда: **FIELD PASS (исторический)** — реально выполнялось на указанной старой сборке; **FIELD FAIL** — дефект пойман физически; **PARTIAL** — только часть карточки или смешанная история; **CI ONLY** — подтверждено на эмуляторе/исходниках, не на телефоне; **NOT VERIFIED** — доказанного физического выполнения карточки на требуемой текущей сборке нет. `LAB` — лишь план, не новая прошивка.

| Карточка | Уже сделанное / найденное свидетельство | Вывод для следующих тестов |
| --- | --- | --- |
| `SETUP-01` | APK и контрольные SHA проверяются CI; установка новой exact-main DEV физически этим документом не подтверждена | CI PASS, отдельный HOST build check при новой APK |
| `UI-01` | REV48 реальные вкладки, Recovery/Fastbootd, центр операций использовались | PARTIAL; не повторять все вкладки без конкретного UI-дефекта |
| `UI-02` | REV48 поворот/IME дал FAIL; **7 ноября последующий аппаратный тест вывода/заголовка после поворота PASS** ([PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45)) | FIELD PASS для конкретного исправленного сценария; не проходить заново, другие элементы UI учитывать отдельно |
| `TERM-01` | Исторические FAIL REV29/REV48 и **физическое подтверждение PASS исправленного расположения вывода/заголовка 7 ноября**, тестировщик сообщает о трёх прогонах; [PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45) | **FIELD PASS ПОВОРОТА УЖЕ ЕСТЬ. Повтор не назначать.** Отдельная задержка анимации при сворачивании не включена в этот PASS |
| `USB-01` | Реальные OTG, USB permissions/RSA, device modes и reconnect на H1/H2/H3 | FIELD PASS по этим связкам, не «никогда не проверялось» |
| `ADB-01` | На H2/H3 реальные model/device/id/getprop/echo и raw stdout | FIELD PASS на конкретных сборках |
| `ADB-02` | Интерактивный PTY на REV51/52 принимал `id/pwd/echo`, выход | FIELD PASS, не обязательный дубль |
| `ADB-03` | H3 REV54 границы 1023/1024/1025 и 16 МиБ → полный SHA | FIELD PASS; **НЕ назначать повторно** без новой причины |
| `FB-01` | Fastboot getvar product/unlocked/is-userspace H2, locked status H3 | FIELD PASS по существующим аппаратам |
| `QUICK-01` | H1 физически видели выбор active/all slots, прошили `vendor_boot_a/b`; H2 Recovery Fastboot | FIELD PASS исторически; это не приёмка каждой кнопки нового UI |
| `MASS-01` | Эмуляторная очередь и исходники проверялись; полного физического UI-прохода на текущем SHA не зафиксировано | CI ONLY / целевой UI пробел, не путать с реальным Mass Flash |
| `FILE-01` | DocumentsUI root иногда пуст, затем восстановился на том же HOST | FIELD intermittent, [#48](https://github.com/Ncorror/NekoFlash-2.0/issues/48) открыта |
| `SIDE-01` | Настоящий Sideload выполнялся; безопасная отмена выбора ZIP не получила отдельного подтверждённого отчёта | PARTIAL; отмена UI не равна реальному Sideload |
| `UNLOCK-01` | На H1 был реальный unlock и верификация unlocked в alpha10; новый текущий UI не доказан | FIELD PASS исторического unlock, current UI — NOT VERIFIED |
| `STAB-01` | Реальные reconnect и смена режимов PASS; поворот терминала ревизии REV48 — FAIL | MIXED, нужен только точный regression |
| `REPORT-01` | Приложение создавало диагностические ZIP, но TestBook Chrome ZIP/JSON не сохранялись | APK ZIP FIELD PASS, HTML FIELD FAIL, [#49](https://github.com/Ncorror/NekoFlash-2.0/issues/49) |
| `TERM-02` | Старый 100%/VERIFY_PENDING зависал после force-stop; код исправлялся | FIELD FAIL старой сборки, [#47](https://github.com/Ncorror/NekoFlash-2.0/issues/47) не принят на новой |
| `TERM-03` | Длинные трассы и raw output есть; точный сценарий 5-кратного close/reopen нового терминала не принят | PARTIAL |
| `TERM-04` | Исходный сдвиг header/body REV48; **последующий аппаратный PASS 7 ноября со скриншотом** ([PR #45](https://github.com/Ncorror/NekoFlash-2.0/pull/45)) | Исправление **физически проверено** в наблюдённом сценарии. Возможный нулевой viewport при одновременном landscape+IME на эмуляторе — отдельное состояние, не основание требовать повтор того же теста |
| `ADB-04` | REV53 10/10 + 2/2 short shell, stdout/exit=0; полная матрица ненулевого exit/stderr для нового SHA не удостоверена | FIELD PASS для успешных команд; PARTIAL для stderr/exit |
| `ADB-05` | Предыдущие stdout и ZIP сохранялись, но нет полной измеренной проверки большого вывода/RAM | NOT VERIFIED performance, [#19](https://github.com/Ncorror/NekoFlash-2.0/issues/19) |
| `ADB-06` | Реальные reconnect и RSA в H1/H2/H3; cold-start идентичность ADB ключа не полностью подтверждена | PARTIAL |
| `FB-02` | getvar:all, unsupported variables, Fastbootd/read-only запросы реально использовались на H2 | FIELD PASS для протокольных read-only, не universal |
| `SAF-01` | ZIP экспорт APK через Android SAF работает; отмена большого импорта в отдельном сценарии не подтверждена | PARTIAL/NOT VERIFIED отмена импорта |
| `SAF-02` | Реальная передача ADB файлов SHA PASS, но это **не** проверка SAF file import | NOT VERIFIED |
| `LOG-01` | Исторические журналы REV48 частично потеряли начало, новые сегменты удерживались | FIELD FAIL полноты старой истории; [#50](https://github.com/Ncorror/NekoFlash-2.0/issues/50) |
| `LOG-02` | Chrome `content://` Copy, JSON и ZIP физически FAIL; обновлённые fallback есть только в коде/CI | FIELD FAIL, не назначать PASS |
| `LOG-03` | REV51 55/55; REV52 103/103; REV53 35/35 + 46/46 ZIP hashes PASS; новые source inventory guard только CI | FIELD PASS для записанных bytes, **NOT VERIFIED** полнота всех source roots |
| `CI-01` | Много запусков CI PASS на конкретных SHA, включая REV55 #501/#502 | CI PASS; не требует нового физического ADB |
| `TERM-06` | Реальная регрессия IME/Unicode в REV29; REV38–49 код/CI исправлялся | FIELD FAIL исторически / CI ONLY исправление |
| `TERM-05` | Код выбора/копирования и автопрокрутки REV39/40 слит; не найден отдельный exact-build physical PASS всех шагов | CI ONLY |
| `ADB-07` | maxdata source fix REV42 есть, обычные реальное ADB передачи позже PASS; отдельный small-maxdata hardware стенд не подтверждён | NOT VERIFIED на специализированном старом peer |
| `ADB-08` | H3 REV54 рекурсивный Push/Pull 512 файлов, SHA-256 все совпали | **FIELD PASS; НЕ повторять автоматически** |
| `ADB-09` | Нажатие Stop в исходной ночной сессии было проверено, но завершение всех последующих потоков/cancel под нагрузкой не доказано | PARTIAL; только конкретный missing cancellation verdict |
| `USB-03` | Кабель однажды физически вылетал при отладке, но это не запланированный контролируемый detach во время успешной ADB передачи | NOT VERIFIED как управляемый regression |
| `FB-04` | Fastboot read-only на H2 PASS; отдельно уже был реальный Fastboot DATA recovery и финальный OKAY | FIELD PASS исторически; не повторять flash только ради getvar |
| `CI-02` | Результаты CI отделены от физического hardware в issue #57 и реестре | CI PASS / процесс учёта; не USB тест |
| `LAB-01` | Для прежних реальных flash был подготовлен разблокированный стенд, но досье резервных копий и восстановления именно нового LAB-раунда не предъявлен | НОВЫЙ LAB: NOT EXECUTED, не переносить старое состояние |
| `LAB-02` | **Реальная прошивка уже была:** H1 `vendor_boot_a/b` 64 МиБ, H2 `recovery` 128 МиБ alpha10.1/REV48 | Исторический FIELD PASS, **новый LAB-02 не выполнялся** |
| `LAB-03` | Fastbootd и post-DATA OKAY на H2 уже наблюдались; новая операция exact current SHA не выполнена | Исторический FIELD PASS, новый LAB-03 не выполнялся |
| `LAB-04` | На 8 ноября Mass Flash ещё не был подтверждён; **9 ноября H2 `main`: реальные `boot/dtbo/recovery` прошли Fastboot OKAY 3/3** | **FIELD PASS 3/3 на H2**, не повторять по старому плану; проверка Android boot отдельно |
| `LAB-05` | Alpha10.1 три Recovery-verified Sideload SUCCESS; REV48 OrangeFox `VERIFY_PENDING`; **9 ноября ROM Lineage SHA mismatch → Recovery error 21 / INSTALL FAIL; ZIP OrangeFox via Lineage Recovery сменил Recovery, без installer exit code** | Исторический PASS ≠ текущая установка; **9 Nov ROM FAIL**, OrangeFox transition FIELD OBSERVED, no install verdict |
| `LAB-06` | На 8 ноября отсутствовал отчёт; **9 ноября на H2 `format userdata` дал Fastboot OKAY, но Android boot FAIL; `-w` завис на `erase:cache` после изменений userdata** | **REAL LAB EXECUTED WITH FAILURE**, [#64](https://github.com/Ncorror/NekoFlash-2.0/issues/64), [#65](https://github.com/Ncorror/NekoFlash-2.0/issues/65). Никакого бессмысленного повторного wipe |

## Главный вывод для следующего цикла

**Самые повторяющиеся старые сценарии:** `ADB-03`, `ADB-08`, `USB-01`, `ADB-01`, `FB-01`, `LAB-02` (в качестве «первой прошивки») и базовые чтения `getprop/id`. Их подтверждённые результаты сохраняем с номером сборки, **не заставляем пользователя выполнять заново из-за смены текста TestBook**.

**Реальные пробелы**: Chrome `content://` сохранение ZIP/JSON, историческая потеря начала журнала и полное сравнение доступных source roots, **отдельная задержка анимации сворачивания и возможный сверхузкий landscape+IME viewport только при новом доказанном воспроизведении (поворот/заголовок уже FIELD PASS)**, lifecycle `VERIFY_PENDING` после force-stop, intermittent DocumentsUI, стресс-память/отмена, причины уже выявленных 9 ноября FAIL при `userdata` и `-w` (не повторять destructive тест), аппаратная приёмка нового безопасного Sideload UI и оставшиеся неизученные функции. Какие именно из них проверяем дальше — решить **в чате**, с индивидуальными командами и сбором результатов, а не путём автоматического прохода всех 43 карточек.

Исходный TestBook и прежние отчёты не менять/не удалять в рамках документального аудита. Здесь не устанавливаются новые галочки «PASS»: факты и границы берутся из [реестра](PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-RU.md), старых документов и issue.
