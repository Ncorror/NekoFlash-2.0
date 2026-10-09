# NekoFlash 2.0 — дополнение к контрольной спецификации

**Дата:** 2026-10-09  
**База:** `NekoFlash-2.0-MASTER-DECISIONS-2026-10-09-RU.md` — сохраняется без правок.  
**Исходник сверки:** `NekoFlash-main-legacy.zip`, `6.0.0-alpha11`, SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`.  
**Статус:** раздел 1 — УТВЕРЖДЁН, раздел 2 — ПРОВЕРЕННЫЙ АУДИТ, раздел 3 — ТОЛЬКО ПРЕДЛОЖЕНИЕ. Не смешивать эти статусы.

## 1. Последние УТВЕРЖДЁННЫЕ решения по массовой прошивке

Переопределяют более ранние предложения и устаревший текст пункта 7.1 мастер-документа.

- Экран массовой прошивки утверждён как список конкретных `раздел → файл`.
- В список добавляются и удаляются операции; оператор переставляет строки. Выполнение **строго сверху вниз** в видимом порядке, без скрытой автоматической сортировки.
- **Один конкретный раздел — одна запись. Дубли не нужны и НЕ разрешены.** Повторное добавление меняет образ существующей записи, не плодит вторую. `boot_a` и `boot_b` — разные адресуемые цели.
- Разрешены реальные разделы со слотами и без слотов, точные ручные имена, как в уже принятой быстрой прошивке.
- Очередь исполняется **последовательно**, останавливается **при первой ошибке**. Ранее завершённые/не выполненные/неопределённые шаги отражаются правдиво в Центре операций.
- Без автоперезагрузки, отдельного обязательного preflight, лишних подтверждений, скрытой перестановки, искусственных лимитов числа операций.
- **НЕ добавлять отдельные функции «Сохранить список», «Загрузить список», шаблоны и профили.** Можно оставить техническое восстановление интерфейса из legacy, но не добавлять GUI управления пакетами.
- Наследовать механизмы `runFlashQueue()`, `flashPartitionDetailed()`, USB-сериализацию/верификацию и диагностику, но устранить устаревшую сортировку и лимиты (`FlashOperationDraft.kt`, `DeviceViewModel.kt`).
- Утверждённая «Быстрая прошивка» и отдельная ручная перезагрузка остаются НЕИЗМЕННЫМИ.

**Хронология исправлений:** промежуточное предложение о разрешении дубликатов было прямо отвергнуто пользователем; последнее решение о запрете дублей имеет приоритет. Предложение о GUI сохранения/загрузки списков было прямо отвергнуто.

## 2. Факты из аудита дополнительных Fastboot-инструментов (НЕ утверждение нового GUI)

### 2.1. Что реализовано

| Возможность | Факт в исходнике | Примечание |
|---|---|---|
| `getvar:<ключ>`, `getvar:all` | `FastbootProtocol.getVar`, `.getVarAll`, `.collectPartitionInventory`; `MainActivity.parseFastbootCommand` | Структурная инвентаризация уже есть; `getvar:all` не равен простому текстовому значению |
| `erase:<partition>` | Terminal parser → `FastbootPartitionCommand` → `DeviceViewModel.runFastbootPartitionCommand` → `.sendCommand` | Есть выбор слота через `--slot` и разрешение имени; реальная поддержка определяется загрузчиком |
| `format <partition>` | Terminal parser создаёт `format:<partition>` через ту же ветку, что erase | **Не является доказанной стандартной реализацией host-side `fastboot format`. Исправить перед выдачей GUI-форматирования за рабочее** |
| `set_active:<slot>` | Raw Fastboot, с повторным `getvar:current-slot` после `OKAY` | Подтверждение фактического слота уже есть |
| `fetch` | `fetchPartition` + `fetchChunk`; поддерживается получение DATA с записью в `.part` и переименованием после успеха | Режим зависит от реализации загрузчика/fastbootd; чтение IN — sync `bulkTransfer`, 16 KiB, не тот же путь что OUT flash |
| `is-logical`, `partition-size`, `partition-type` | `inspectLogicalPartition` | Результаты логируются; GUI-представление ещё не проектировалось |
| `create/delete/resize-logical-partition` | Разбор терминала и raw команды; также метод `runLogicalPartitionCommand` | Мутации обычно поддерживает Fastbootd; реальные FAIL должны передаваться без подмены |
| `update-super` | Терминал: выбор файла, затем `downloadAndRun(..., update-super:...)` | Требует корректного образа метаданных; ограничения single-download сохраняются |
| `snapshot-update`, `gsi`, `flashing`, `oem`, raw команды | Разбор и прямой passthrough | Не подменять продвинутые команды «универсальными» GUI-кнопками; полный терминал должен оставаться доступен |
| `boot <file>` | `downloadAndRun(file, "boot")` | Файл загружается в RAM загрузчика, а затем команда boot; это не прошивка раздела |
| `fastboot flashall` / `fastboot update` | Терминальный парсер сообщает, что desktop batch orchestration **не реализована** | Не путать с GUI массовой очередью `runFlashQueue()` |

### 2.2. Подтверждённые расхождения и задачи, которые нельзя замаскировать GUI

1. `format` в терминале развернут в wire `format:<partition>`, тогда как официальный AOSP desktop `fastboot format` обычно **сам генерирует образ ФС** с учётом `partition-type`, `partition-size`, geometry и отправляет его обычной передачей/прошивкой. Не рекламировать простой wire как эквивалент; для корректного GUI требуются хостовая подготовка ФС или явно распознаваемая OEM-специфика. Источник AOSP: https://chromium.googlesource.com/aosp/platform/system/core/+/refs/heads/upstream/fastboot/fastboot.cpp
2. `MainActivity.showRebootMenu()` направляет Fastbootd как `reboot:fastboot`, но `parseFastbootCommand("reboot fastboot")` — как `reboot-fastboot`. Проверить и унифицировать фактическую wire-команду со спецификацией/AOSP, не менять согласованное место кнопки. Это **расхождение в legacy**, не повод для автоперезагрузки.
3. Часть опций терминального host CLI (`-S`, `--disable-verity`, `--disable-verification`, `flashall`, `update`, `wipe-super`) в текущем парсере не реализует соответствующей desktop-host семантики. Некоторые отвергаются или просто отправляются на USB. Не заявлять полную совместимость с desktop fastboot до реализации.
4. Для `fetch` нужно учитывать отказ DATA посреди чтения, достаточное место в файловом хранилище, `.part` файл и реальную полноту результата; `max-fetch-size`/поблочное чтение поддерживаются по условию и могут отсутствовать у устройства.
5. Для `snapshot-update merge` в legacy есть verify-политика, ожидающая `snapshot-update-status=NONE`; проверить поведение на реальных состояниях snapshot, не трактуя неполный merge как подтверждённый успех.
6. `getvar:all` и инвентаризация могут быть неполными и требуют строго прочитанной топологии; `UNKNOWN` не является `A_ONLY`. Уже утверждённые правила работы со слотами сохраняются.
7. Лог диагностики при `unlocked=no` сообщает, будто прошивка блокируется приложением, но `flashPartitionDetailed` не содержит соответствующего явного запрета — привести диагностику к фактическому поведению, а не вводить новый искусственный запрет.
8. Raw/OEM passthrough доступен через полноэкранный терминал и должен остаться. USB owner, serial transactions, разрыв повреждённой сессии, реальные OKAY/FAIL — общие для всех способов запуска.

### 2.3. Где проверено

- `app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt`: `showRebootMenu` ~1856–1912; `handleFastbootTerminalCommand` ~921–953; `parseFastbootCommand` ~1088–1250; `page_fastboot.xml` — прежние элементы быстрого Flash.
- `app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt`: `runFastbootCommand`, `runFastbootDownloadAndRun`, `runFastbootLogicalPartitionCommand`, `inspectFastbootLogicalPartition`, `runFastbootFetch`, `runFastbootPartitionCommand` ~1009–1075.
- `app/src/main/java/ru/forum/adbfastboottool/FastbootProtocol.kt`: `sendCommand`, `runTerminalCommand`, `getVar`, `getVarAll`, `collectPartitionInventory` ~344–664; slot targeting ~813–878; flash ~880–996; dynamic/fetch ~997–1109; download+run ~1115–1185; fetch read ~2310–2380.
- AOSP fastboot docs: https://android.googlesource.com/platform/system/core/+/master/fastboot/README.md ; AOSP client: https://chromium.googlesource.com/aosp/platform/system/core/+/refs/heads/upstream/fastboot/fastboot.cpp ; Fastbootd reference: https://source.android.com/docs/core/architecture/bootloader/fastbootd

## 3. Предложение по интерфейсу (ЕЩЁ НЕ УТВЕРЖДЕНО)

Предлагается **не менять** утверждённую «Быструю прошивку» и «Массовую прошивку». В существующей нижней вкладке **«Прошивка»** разместить отдельный внутренний раздел **«Инструменты Fastboot»**:

- **Сведения:** `getvar` по ключу и общий обзор/инвентаризация.
- **Разделы:** `erase`, полноценный `format` *после исправления host-side семантики*, `fetch` с выбором файла для сохранения; существующее правило слота.
- **Слоты:** `set_active` с фактическим подтверждением `current-slot`.
- **Fastbootd:** инспекция `is-logical` / размера / типа, управление create/delete/resize и `update-super` по поддержке устройства.
- **Расширенные команды:** оставлять полноэкранному терминалу (raw/OEM/flashing/snapshot/gsi) во избежание дублирования и ненужной кнопочной россыпи. Терминал не вызывает Центр операций.

Для коротких запросов — обычный вывод результата. Для длительных GUI-запросов (например `fetch`, `update-super`) — ранее утверждённый Центр операций; никаких навязанных preflight-окон и искусственных стопоров. Никаких автоперезагрузок.

**Ожидаемое решение пользователя:** согласовать или скорректировать саму организацию инструментов. Пока **НЕ считать этот раздел утверждённым**, не создавать новые кодовые изменения или APK.
