<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Mass Flash Specification & Fastboot Audit

### Approved mass flashing queue rules and additional fastboot tools audit

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Mass Flash Specification & Fastboot Audit

**Date:** October 9, 2026  
**Baseline:** `NekoFlash-2.0-MASTER-DECISIONS-2026-10-09-RU.md`  
**Reference Source:** `NekoFlash-main-legacy.zip`, `6.0.0-alpha11`, SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`.  
**Status Structure:** Section 1 — APPROVED, Section 2 — VERIFIED AUDIT, Section 3 — PROPOSAL (historically pending).

---

## 1. Approved Decisions for Mass Flashing

Overrides earlier drafts and legacy section 7.1 notes:

- **Queue Representation:** Mass flashing is structured as a clear list of concrete `partition → image file` mappings.
- **Queue Modification:** Operations can be added, deleted, or manually reordered. Execution is **strictly top-to-bottom** in visible order, with no background re-sorting.
- **Single Target Invariant:** **One specific partition — one entry. Duplicates are disallowed.** Re-adding a partition replaces the target image rather than creating a secondary entry. (`boot_a` and `boot_b` are treated as distinct addressable targets).
- **Partition Scope:** Supports slotted and unslotted partitions with explicit manual entry, matching Quick Flash rules.
- **Execution Flow:** Executes **sequentially** and halts **immediately upon the first failure**. Completed, skipped, and unreached steps are faithfully reflected in the Operation Center.
- **No Hidden Actions:** No automatic reboots, mandatory preflight popups, extra confirmation steps, or artificial queue limits.
- **No GUI Presets:** **Do NOT add dedicated "Save List" or "Load List" GUI buttons, templates, or profile managers.** Legacy state restoration remains internal.
- **Inherited Engines:** Inherit `runFlashQueue()`, `flashPartitionDetailed()`, USB serialization, and diagnostics, while purging obsolete sorting and capacity limits (`FlashOperationDraft.kt`, `DeviceViewModel.kt`).

---

## 2. Additional Fastboot Tools Audit Findings

### 2.1 Implemented Capabilities in Baseline

| Capability | Legacy Source Location | Operational Detail |
|---|---|---|
| `getvar:<key>`, `getvar:all` | `FastbootProtocol.getVar`, `.getVarAll`, `.collectPartitionInventory` | Structural inventory exists; `getvar:all` is parsed structurally. |
| `erase:<partition>` | Terminal parser → `FastbootPartitionCommand` → `DeviceViewModel` | Slot selection via `--slot`; support depends on bootloader implementation. |
| `format <partition>` | Terminal parser forwards to `format:<partition>` wire | **Does not match desktop AOSP `fastboot format` filesystem creation.** Must be fixed before advertising in GUI. |
| `set_active:<slot>` | Raw Fastboot with verified `getvar:current-slot` check | Active slot verification implemented. |
| `fetch` | `fetchPartition` + `fetchChunk` with `.part` staging | IN-stream bulk transfer (16 KiB blocks); depends on bootloader/fastbootd support. |
| Dynamic partition query | `inspectLogicalPartition` | Queries `is-logical`, `partition-size`, `partition-type`. |
| Dynamic partition mutation | `runLogicalPartitionCommand` | `create-logical-partition`, `delete-logical-partition`, `resize-logical-partition`. |
| `update-super` | Terminal parser → `downloadAndRun(..., update-super:...)` | Requires valid metadata image; single-download limits apply. |
| `boot <file>` | `downloadAndRun(file, "boot")` | Boots RAM image directly without partition flash. |
| Advanced commands | Passthrough in terminal | `snapshot-update`, `gsi`, `flashing`, `oem`, raw commands. |

### 2.2 Critical Implementation Differences

1. **Host-Side `format`:** Desktop `fastboot format` creates filesystems locally matching partition geometry; simple wire command transmission is not universally supported.
2. **Reboot Command Unification:** Reconcile `reboot:fastboot` vs `reboot-fastboot` wire formats between menu handlers and terminal parsers.
3. **Partial Stream Safety:** `fetch` must verify available storage, manage `.part` files, and validate transfer completeness before concluding.
4. **State Verification:** `snapshot-update merge` must verify status is `NONE` before marking success.

---

## 3. Fastboot Tools UI Structure (Reference)

Subdivided into four functional blocks within the **Flash** tab:
- **Information:** `getvar` inspection and partition inventory.
- **Partitions:** `erase`, proper `format` (post-remediation), and `fetch` with destination file picker.
- **Slots:** `set_active` slot switching with confirmed `current-slot`.
- **Fastbootd:** Logical partition inspection, management, and `update-super`.
- **Expert Commands:** Reserved for full-screen terminal (raw/OEM/flashing).

---

## Русская версия / Russian Version

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

## 3. Предложение по интерфейсу (ЕЩЁ НЕ УТВЕРЖДЕНО)

В существующей нижней вкладке **«Прошивка»** разместить отдельный внутренний раздел **«Инструменты Fastboot»**:
- **Сведения:** `getvar` по ключу и общий обзор/инвентаризация.
- **Разделы:** `erase`, полноценный `format` *после исправления host-side семантики*, `fetch` с выбором файла для сохранения; существующее правило слота.
- **Слоты:** `set_active` с фактическим подтверждением `current-slot`.
- **Fastbootd:** инспекция `is-logical` / размера / типа, управление create/delete/resize и `update-super` по поддержке устройства.
- **Расширенные команды:** оставлять полноэкранному терминалу (raw/OEM/flashing/snapshot/gsi).
