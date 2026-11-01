<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Approved Sideload REV3 Specification

### Approved dedicated Sideload tab specification and recovery workflow

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Approved Sideload Screen Specification

**Date:** October 10, 2026  
**Status:** **APPROVED** by user ("Да фиксируем").  
**Design Version:** SIDELOAD REV3, frozen without modification.

### Key Decisions

- The third tab in the bottom navigation bar is named **Sideload** (not "ADB").
- The screen contains exclusively the standard Sideload workflow: concise Recovery guide (**Apply update → Apply from ADB**), display of the selected ZIP package name, **"Choose ZIP for Sideload"**, and **"Import file"**.
- Approved global shell elements, including the persistent USB status bar and independent full-screen terminal, remain unchanged.
- **Do not add** Push/Pull, APK installation, Shell, reboot controls, diagnostics, or other ADB utilities into the Sideload tab.
- All previously approved DESIGN REV2 specifications remain valid; **this decision takes explicit precedence** over earlier references to the tab being called "ADB" and over rejected ADB drafts.
- Visual presentation strictly adheres to the approved HTML mockup and PNG reference in the design archive.

### Implementation Scope

This document represents visual design consensus, **not** completed Android implementation, APK build qualification, or physical hardware testing. The original baseline checkpoint is preserved without modification.

### Next Steps

Future implementation work is based on the original `NekoFlash-main-legacy.zip`. The Sideload screen design must not be altered without direct user authorization.

---

## Русская версия / Russian Version

# NekoFlash 2.0 — фиксация согласованного экрана Sideload

**Дата:** 10 октября 2026 года  
**Статус:** УТВЕРЖДЕНО пользователем («Да фиксируем»).  
**Версия макета:** SIDELOAD REV3, без каких-либо изменений.

## Утверждено

- Третья вкладка в нижней навигации называется **Sideload** (не «ADB»).
- Внутри только штатное меню Sideload: краткая инструкция для Recovery (**Apply update → Apply from ADB**), отображение выбранного ZIP, **«Выбрать ZIP для Sideload»**, **«Импортировать файл»**.
- Утверждённые общие элементы оболочки, в том числе постоянная USB-панель и отдельный полноэкранный терминал, остаются без изменений.
- **Не добавлять** Push/Pull, установку APK, Shell, перезагрузки, диагностику или иные инструменты ADB внутрь вкладки Sideload.
- Все ранее утверждённые экраны и решения DESIGN REV2 сохраняются; **это уточнение имеет приоритет** над старым названием вкладки «ADB» в документах REV2 и над отклонённым черновиком ADB.
- Внешний вид Sideload соответствует согласованным HTML-макету и PNG из этого архива.

## Граница реализации

Речь идёт о фиксации дизайна, **не** о завершённой реализации Android-кода, сборке APK, испытаниях на телефоне либо изменении GitHub. Оригинальный checkpoint REV2 включён без изменения, как и REV3.

## Дальнейшая работа

Следующие работы вести на основе оригинального `NekoFlash-main-legacy.zip`. Не переделывать экран Sideload без нового прямого решения пользователя.
