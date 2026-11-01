<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Approved Unlock REV4 Specification

### Approved Xiaomi Mi Unlock workflow screen, visual states, and reconnect verification

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Approved Unlock REV4 Specification

**Date:** October 10, 2026  
**Status:** **APPROVED** by user ("Фиксируем").  
**Baseline:** Design mockup `NekoFlash-2.0-UNLOCK-DRAFT-REV4-2026-10-10.html` and two PNG state assets. The legacy `DRAFT` label in file naming is retained for provenance tracking.

### Approved UI Composition

- The **Unlock** tab is dedicated solely to Xiaomi **Mi Unlock**, rather than serving as a miscellaneous Fastboot command surface.
- Composed of two compact cards: **Mi Account** and **Bootloader**.
- **Mi Account:** Sign-in, authenticated state, sign-out; manual `dataCenterZone` selection is tucked inside an expandable advanced parameters sheet to maintain a clean default interface.
- **Bootloader:** Shows current lock status; the unlock action button is active only when all prerequisites are met (authenticated Mi account + active Fastboot connection). An already unlocked device does not offer redundant unlocking.
- **Data Wipe Warning:** Prominently displays the warning that unlocking may completely wipe user data.
- **Verified Outcome:** "Success" is only reported after device reboot/reconnect and verification via `getvar:unlocked=yes`, never immediately after transmitting the initial unlock wire payload.
- Two approved visual states: `NO-ACCOUNT` and `READY-DEMO` (purely a visual mockup for review).

### Invariants and Inherited Decisions

- Do not add general ADB/Fastboot tools, diagnostic benches, fake buttons, manual reboot triggers, or mode switchers to Unlock.
- Persistent top USB status bar, terminal button, bottom navigation, and dark orange theme remain invariant.
- Third tab remains strictly **Sideload** per approved REV3.
- Earlier REV2 and REV3 decisions remain in force unless explicitly amended.
- Supersedes `NekoFlash-2.0-UNLOCK-DRAFT-DECISIONS-2026-10-10.md`, advancing its status to **APPROVED**.

### Implementation Boundary

This document records the **REV4 design freeze**. It does not constitute source code modifications, APK packaging, or physical hardware unlocking. Production implementation builds upon the canonical `NekoFlash-main-legacy.zip`.

---

## Русская версия / Russian Version

# NekoFlash 2.0 — утверждённый экран Unlock REV4

**Дата:** 10 октября 2026 года  
**Статус:** **УТВЕРЖДЕНО пользователем («Фиксируем»).**  
**Основа:** дизайн-макет `NekoFlash-2.0-UNLOCK-DRAFT-REV4-2026-10-10.html` и два PNG состояния. Имя исходного файла `DRAFT` сохранено для проверки происхождения, несмотря на утверждение дизайна.

## Утверждённый состав

- Вкладка **Unlock** служит для **Mi Unlock** Xiaomi, а не как универсальный набор Fastboot-команд.
- Компактные разделы: **Mi-аккаунт** и **Загрузчик**.
- Для аккаунта: вход, состояние авторизации, выход; ручной `dataCenterZone` находится в раскрываемых дополнительных параметрах, не загромождает основной экран.
- Для загрузчика: отображение состояния, доступность действия разблокировки только при необходимых реальных условиях, включая авторизованный Mi-аккаунт и подключённое Fastboot-устройство. Уже разблокированный загрузчик не требует повторной разблокировки.
- Видимое предупреждение о возможном полном удалении данных при разблокировке.
- **Успешно** показывать только после переподключения устройства и подтверждения состояния через `getvar:unlocked=yes`, а не сразу после отправки команды.
- Два утверждённых визуальных состояния: `NO-ACCOUNT` и `READY-DEMO`. **READY-DEMO** — исключительно имитация, не реальная авторизация/USB.

## Ограничения и наследуемые решения

- Не добавлять в Unlock общие ADB/Fastboot инструменты, отдельную диагностику, тестовые кнопки, самостоятельную перезагрузку или переключатель USB-режима.
- Сохраняются верхняя постоянная USB-панель, терминал, навигация, тёмно-оранжевая визуальная система и остальные утверждённые экраны.
- Третья вкладка остаётся **Sideload** в точности по ранее **утверждённому REV3**. Никакой дополнительной функциональности внутри неё.
- Ранее утверждённые решения REV2 и REV3 остаются в силе, если не противоречат более поздним явным изменениям.
- Файл `NekoFlash-2.0-UNLOCK-DRAFT-DECISIONS-2026-10-10.md` в архиве — историческая версия *до утверждения*. Этот документ заменяет её статус на **УТВЕРЖДЕНО**.

## Граница текущего этапа

Зафиксировано **согласование дизайна REV4**, а не изменение исходного Android-приложения. Android-код, GitHub, APK, USB-проверки и реальная разблокировка на устройстве этим пакетом не затрагивались. Для реализации опираться на исходник `NekoFlash-main-legacy.zip` из предыдущих checkpoint.
