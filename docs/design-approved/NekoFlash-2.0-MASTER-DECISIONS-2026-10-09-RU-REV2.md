<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Master Decisions Register

### Master baseline of approved decisions, UI shell, navigation, and operation flows

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Master Decisions Register

**Checkpoint Version:** DESIGN-FREEZE-2026-10-09 / rev. 2  
**Date:** October 9, 2026  
**Status:** Architectural design freeze and audit; does not claim completed compilation, merge, hardware qualification, or code implementation.  
**Priority:** Records decisions as of the approval of **Fastboot Tools** and **Mass Flashing**. Direct user decisions supersede earlier mockups, assistant proposals, and historic audit drafts.

> **Core Rule:** Do NOT redesign approved screens, do NOT introduce unreviewed features, do NOT mix canonical `NekoFlash-main-legacy` with `NekoFlash-Pro` or experimental side builds. This is a baseline document for compliance verification.

---

## 0. Executive Control Summary

- **APPROVED:** Welcome screen; 5-tab bottom navigation shell; persistent top USB status bar with automatic mode detection and detail sheet; Home screen dedicated solely to device info (photo + two independent expandable cards); full-screen terminal accessible to the right of the USB bar; contextual modal Operation Center with real-time logs, queue confirmation, floating pill indicator, background execution, error handling, recovery, and comprehensive reports; stop-on-first-error mass flashing; Quick Flash screen (supporting slotted and unslotted partitions); independent manual reboot menu; Mass Flash queue screen; dedicated Fastboot Tools screen within the Flash tab.
- **FORBIDDEN TO REINTRODUCE:** Automatic device reboot of any kind; separate preflight/confirmation modals before normal quick flash; large recovery-first prioritization; persistent Operation Center on Home; Operation Center popups triggered from terminal commands; artificial test/debug buttons; arbitrary limits on operations/logs or silent history deletion; unverified rewrites of the core USB transport engine.
- **TECHNICAL ITEMS IN PROGRESS:** Direct large-file filesystem access and RAW/sparse transfers exceeding 4 GiB; host-side `format` semantics and `fastbootd` wire reconciliation; ADB/Settings screens; lossless storage without silent rotation; brightness dimming behavior; ViewModel-independent background execution; dedicated full-screen log viewer; Mi Unlock (deferred until other tabs are fully qualified).

---

## 1. Baseline and Project Boundaries

### 1.1 Single Canonical Source of Verified Code

- Original archive: **`NekoFlash-main-legacy.zip`**.
- SHA-256: **`74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`**.
- Duplicate copy `NekoFlash-main-legacy(2).zip` is byte-for-byte identical; verified via SHA-256 and `unzip -t`.
- Code Version: **`6.0.0-alpha11`**, `versionCode=234`, `applicationId=ru.forum.adbfastboottool`.
- Do not substitute `NekoFlash-Pro` or experimental prototypes as reference baselines.
- Preserve qualified ADB, Fastboot, Native USBFS, Mi Unlock, and diagnostics protocol logic. Architectural reorganization must not alter wire semantics.

### 1.2 Deliverables at this Checkpoint

- Visual mockups and UX specifications drafted and approved.
- Original code archive and static audits reviewed against physical hardware evidence.
- Baseline code was preserved untouched; new APKs were not generated or pushed to GitHub during this phase.

---

## 2. Core Visuals and Navigation — APPROVED

### 2.1 Unified Styling

- Professional Android utility for service technicians; **dark theme** with signature **orange accent** identical to legacy.
- Clean surfaces without unnecessary dark overlays, cluttered cards, or placeholder debug buttons.
- Fully respects system status bars, Android gesture / 3-button navigation, software keyboards, and window insets.

### 2.2 Welcome Screen

- **Retain original artwork unchanged** (night city skyline, girl with smartphone, Android mascot, cat).
- Full-screen cold start; standard NekoFlash title; single orange button: **"Enter NekoFlash"**.
- Displayed exclusively on cold launch, not on ordinary Activity resume.
- No mandatory onboarding storage gates; runtime permissions requested in context when needed.

### 2.3 Primary Navigation

- **Exactly five tabs:** `Home` / `Flash` / `ADB` (later refined to `Sideload`) / `Unlock` / `Settings`.
- Fixed bottom navigation above system navigation insets.
- No separate sixth tab for "Terminal" or "Operations".

### 2.4 Persistent Top USB Status Bar

- Color-coded bar at the top of all workflow pages displaying detected USB connection state:
  - `ADB • SYSTEM`, `ADB • RECOVERY`, `ADB • SIDELOAD`;
  - `FASTBOOT`, `FASTBOOTD`;
  - `NO DEVICE`, `CONNECTING`, `ERROR`, `UNKNOWN`.
- Tapping opens a bottom sheet showing VID, PID, endpoints, interface names, authorization, device selection, and quick diagnostics.
- Manual mode switching controls are strictly excluded from the status bar.

---

## 3. Home Screen — Pure Device Information — APPROVED

- Displays only device identity and hardware specifications.
- Persistent Operation Center, task centers, and action buttons are removed from Home.
- Structure:
  - Device photo on left; model name and key specs on right.
  - Lower fields: codename, Android version, bootloader lock state, active slot.
  - Two expandable cards: "Advanced Info" (live device properties via `getprop`/`getvar`) and "Model Specs" (offline hardware database).
  - Explicit freshness states: actual, last-known, unavailable. Never guess hardware data.

---

## 4. Professional Terminal — APPROVED

- Accessible via a dedicated icon immediately to the right of the top USB status bar across all tabs.
- Full-screen terminal surface supporting large scrollback, command input, clipboard paste, and IME management.
- Defaults to HOST CLI commands; PTY allocated only for interactive `adb shell`.
- Terminal commands operate with full transport safeguards and logging, but **never raise the modal Operation Center**.

---

## 5. Contextual Operation Center — APPROVED

### 5.1 Invocation

- Appears as a modal popup solely for long-running GUI-initiated tasks (flashing, sideloading, unlock).

### 5.2 Layout

- Prominent current operation title, percentage / indeterminate progress bar, `step X of Y`, bounded nearby-step queue, expandable scrolling log details (100–120dp height), and explicit cancellation button.

### 5.3 Floating Indicator

- Minimizing collapses the operation into a compact floating pill positioned above the bottom navigation bar across all tabs.
- Displays live percentage or progress spinner; tapping reopens the full Operation Center modal.

### 5.4 Concurrency and Queuing

- Strict **single-ownership** of the USB transport.
- When an operation is in flight, initiating another USB task prompts: "Add to Queue" or "Cancel".
- Queued operations execute sequentially once the active operation finishes.

### 5.5 Cancellation and Stages

- Cancellation transitions to a safe-cleanup state with indeterminate progress rather than freezing the last transfer percentage.
- Unsafe cancellation during active device-side partition mutation is prevented.

### 5.6 Background Execution and Alerts

- Foreground Service with wake lock ensures operation continues when the app is backgrounded or screen dims.
- Quiet notification on completion without disruptive sound or vibration.

### 5.7 USB Recovery

- Distinguishes between intentional reboots, unexpected disconnects, Fastboot broken sessions, and Native USBFS stalls.
- Never misinterprets a USB reconnection as proof of operation success.

### 5.8 Outcome Reporting

- Final outcomes: `SUCCESS`, `FAILED`, `CANCELLED`, `VERIFY_PENDING`.
- Ambiguous or unconfirmed operations remain `VERIFY_PENDING` until validated.

### 5.9 Mass Flash Execution Rules

- Strict top-to-bottom execution order; halts immediately on first error.
- One partition per entry; duplicates disallowed.
- No automatic reboots or separate GUI preset manager buttons.

### 5.10 Logging and Export

- Lossless logging: telemetry is preserved without silent segment deletion.
- Clean export: authentication cookies, passwords, and sensitive identifiers are redacted before sharing.

---

## 6. Quick Flash Screen — FINAL APPROVAL

- Single clean interface for flashing individual partitions:
  - Selected partition dropdown (`boot`, `init_boot`, `vendor_boot`, `recovery`, `dtbo`, `vbmeta`, or custom manual name).
  - Slotted partition target selection (Current Slot vs All Slots) when `has-slot=yes`.
  - Image file selector.
  - Prominent "Flash Partition" button.
- Separate manual Reboot menu to Bootloader, Recovery, Fastbootd, or System.
- No auto-reboot upon flash completion.

---

## 7. Fastboot Tab Architecture

- The **Flash** tab encompasses three clean sub-views:
  1. **Quick Flash** (single partition).
  2. **Mass Flash** (ordered queue of partition-image pairs).
  3. **Fastboot Tools** (four sub-blocks: getvar, partitions, slots, fastbootd).

---

## Русская версия / Russian Version

# NekoFlash 2.0 — мастер-документ утверждённых решений

**Контрольная версия:** DESIGN-FREEZE-2026-10-09 / rev. 2  
**Дата фиксации:** 9 октября 2026 года  
**Язык:** RU  
**Статус:** проектирование и аудит; **не** заявление о завершённой сборке, мерже, запуске на устройстве или внедрении функций.

## 0. Краткая контрольная сводка

**Утверждено:** Welcome; оболочка с пятью вкладками; постоянная USB-панель с автоопределением режима и подробной шторкой; главная как информационный экран устройства; фото и два независимых раскрываемых раздела; вход в полноэкранный терминал справа от USB-панели; контекстный Центр операций с живыми подробностями, очередью, плавающим индикатором, фоном, ошибками, восстановлением и полными отчётами; результаты и автоостановка массовой прошивки при первой ошибке; экран «Быстрая прошивка» (включая прошивку без слотов); отдельная **ручная** перезагрузка; экран **«Массовая прошивка»** со списком операций; отдельный экран **«Инструменты Fastboot»** внутри вкладки «Прошивка».

**Запрещено возвращать:** автоперезагрузку любого вида; отдельный preflight/окно подтверждения перед обычной быстрой прошивкой; приоритетную большую кнопку `recovery`; Центр операций на главной; вызов Центра операций из терминала; дополнительные тестовые кнопки; лимиты на количество операций/длину логов/тихое удаление истории; произвольное переписывание отлаженного USB-движка.

## 1. Исходная база и границы проекта

### 1.1. Единственный эталон проверенного кода

- Оригинальный архив: **`NekoFlash-main-legacy.zip`**.
- SHA-256: **`74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`**.
- Версия кода: **`6.0.0-alpha11`**, `versionCode=234`, `applicationId=ru.forum.adbfastboottool`.

## 2. Базовый внешний вид и навигация — УТВЕРЖДЕНО

### 2.1. Общий стиль

- Профессиональный Android-инструмент для сервисной работы, **тёмная тема** с фирменным **оранжевым акцентом**, как в legacy.
- Поля и поверхности лёгкие, без лишнего затемнения. Учитывать системные inset'ы.

### 2.2. Welcome

- **Исходную иллюстрацию сохранять без изменения**.
- Полный экран; единственная заметная оранжевая кнопка **«Войти в NekoFlash»**.
- Холодный запуск; без обязательного storage gate.

### 2.3. Главная навигация

**Ровно пять вкладок:** `Главная` / `Прошивка` / `ADB` / `Unlock` / `Настройки`.
