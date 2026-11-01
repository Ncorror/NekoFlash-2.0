<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Approved Fastboot Tools Specification

### Decision freeze: getvar, partitions, slots, and fastbootd tools

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Approved Fastboot Tools Design

**Status:** The "Fastboot Tools" UI design is **APPROVED** by the user following interactive mockup review (October 9, 2026). This file records the approved design decision; it does not claim completed implementation or hardware qualification.

**Four Approved Functional Blocks:**
1. **Info / `getvar`:** Property queries and partition inventory overview.
2. **Partitions & Images:** `erase`, `format`, `fetch`, and `boot`.
3. **Slots:** `set_active` slot switching and live `current-slot` verification.
4. **Fastbootd:** Dynamic partition inspection (`is-logical`, size, type), partition management (`create`, `delete`, `resize`), and `update-super`.

Within the unified "Flash" tab, the user switches between these four sub-blocks. The persistent top USB status bar, terminal button on the right, bottom navigation, and orange dark-theme styling are preserved.

**Invariants — Do Not Modify:**
- Previously approved Quick Flash and Mass Flash specifications remain frozen.
- Do not add automatic device reboots, separate preflight confirmation dialogs, placeholder test buttons, or arbitrary command filters.
- Raw, OEM, and advanced diagnostic commands remain in the dedicated full-screen terminal without invoking the Operation Center.

**Implementation Realities:**
- `format` must be aligned with true Android Fastboot host semantics (generating host-side filesystems when needed rather than sending plain wire commands).
- `fastbootd` reboot transitions must be unified with standard wire commands.
- `fetch` must only finalize output files after full data transfer verification.
- All actions depend on bootloader-supported features and valid connection states.

**Mass Flash Rules Confirmed:**
- Strict top-to-bottom visible queue execution.
- One specific partition target per entry; duplicates are prohibited and overwrite existing entries.
- Separate GUI buttons for saving/loading profiles or presets are disallowed.

**Reference:** `NekoFlash-2.0-MASTER-DECISIONS-2026-10-09-RU-REV2.md`, §§ 6–7.

---

## Русская версия / Russian Version

# NekoFlash 2.0 — зафиксировано 9 октября 2026

**Статус:** дизайн «Инструменты Fastboot» УТВЕРЖДЁН пользователем после интерактивного макета. Этот файл отражает решение, не является подтверждением реализованного или протестированного приложения.

**Утверждены четыре блока:** 1) Сведения / getvar; 2) Разделы и образы / erase, format, fetch, boot; 3) Слоты / set_active и проверка current-slot; 4) Fastbootd / create, delete, resize и update-super. В одном экране внутри вкладки «Прошивка» переключается только нужный блок. Верхняя USB-панель, терминал справа, нижняя навигация и единый стиль сохраняются.

**Не менять:** ранее утверждённые Быструю и Массовую прошивки. Не добавлять автоперезагрузки, отдельные preflight/подтверждения, тестовые кнопки или ограничения команд. Raw/OEM и другие экспертные команды остаются в полноэкранном терминале без вызова Центра операций.

**Не путать дизайн с работоспособностью:** `format` должен быть приведён к реальной семантике Android Fastboot; переход Fastbootd унифицирован; `fetch` завершает файл лишь после подтверждения полного чтения. Все функции требуют корректных ответов и условий конкретного загрузчика. Это задачи реализации и тестов.

**Дополнительно учтены последние решения массовой прошивки:** видимый порядок сверху вниз; одна конкретная цель — одна запись; повторное добавление заменяет существующую запись; сохранение и загрузка списков отдельными GUI-кнопками запрещены.

**Следующий этап:** аудит вкладки ADB по original legacy без изменений утверждённых экранов.

Полное описание и решение: `NekoFlash-2.0-MASTER-DECISIONS-2026-10-09-RU-REV2.md`, §§ 6–7.
