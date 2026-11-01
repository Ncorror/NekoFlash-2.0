<div align="center">

<img src="../artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# NekoFlash 2.0 — Mi Unlock Static Legacy Audit

### Static audit of Mi Unlock source implementation, pending states, and risk mitigation

English&nbsp;&nbsp;|&nbsp;&nbsp;[Русский](#русская-версия--russian-version)

</div>

## Mi Unlock Static Audit

**Date:** October 10, 2026  
**Scope:** Design audit; NOT Android implementation or physical hardware testing.  
**Baseline:** `NekoFlash-main-legacy.zip` (SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`).

### Actual Implementation in Legacy Source

1. `MainActivity.kt:2331–2493`: The `Unlock` tab is constructed from two cards: **Mi Account** and **Bootloader**. Sign-in is handled via `MiLoginActivity`; upon success, ID, region, and `dataCenterZone` are displayed. Manual zone selection and sign-out controls exist. Bootloader state and action availability depend on Fastboot diagnostics.
2. `MainActivity.kt:1571–1622`: Following web login, an `unlockApi` session token is acquired via `MiAccountClient.exchangeToken`.
3. `DeviceViewModel.kt:885–990`: Execution flow: `getVar(product) → readXiaomiUnlockToken → nonce → checkClear → requestUnlock → stageAndOemUnlock`. This transitions to **VERIFY_PENDING**, not immediate SUCCESS. Device reconnection with `getvar:unlocked=yes` is required for confirmation.
4. `DeviceViewModel.kt:1336–1367,1451–1518`: Pending verification stores product and serial (when available), validates matching identity after reconnect, rejects mismatched devices, marks success on `unlocked=yes`, failed on proven `no`, and waits if inconclusive.
5. `MiUnlockClient.kt`: Server interactions require live Xiaomi infrastructure and valid accounts.

### Proposed REV4 Design Adjustments

- Preserve persistent top USB status bar, full-screen terminal icon on the right, and five bottom tabs (third is **Sideload**).
- Compactly display **Mi Account** (unauthenticated → login; authenticated → status, expandable zone settings, logout) and **Bootloader** (actual status, Fastboot requirement, data wipe warning, primary action).
- If diagnostics confirm `unlocked=yes`, suppress redundant unlock actions.
- Keep server zone management in expandable account settings. Do not put raw commands into Unlock.
- Display success only after verified reconnect.
- Mockups are visual prototypes (`?preview=ready` simulates UI without live USB/network commands).

### Technical Safeguards for Future Implementation

- **[P0] Device Identity Integrity:** Avoid device mismatch upon reconnection; correlate pending unlock with re-enumerated target using authoritative descriptors. Disallow concurrent operations on busy USB. Never infer unlock success from `oem unlock` ACK alone without `getvar:unlocked=yes`.
- **[P1] Operation Lifecycle & Reports:** Propagate verified outcome to Operation Center, history log, and exported reports. Prevent pending markers from timing out without explicit outcome resolution.
- **[P1] Server Warning Propagation:** Preserve prominent data-wipe warnings before initiating the unlock sequence.
- **[P1] Credential Security:** Authentication cookies, `passToken`, `ssecurity`, device tokens, and encrypted payloads are confidential. Never write them into exported logs, share dialogs, or UI mockups.
- **[P2] Compatibility & Diagnostics:** Preserve detailed server error messages (including Xiaomi business error 20045 and zone mismatches).
- **[P2] Testing Strategy:** Validate using mock transports before physical hardware checks. Never trigger unlock on user hardware without explicit consent.

---

## Русская версия / Russian Version

# NekoFlash 2.0 — Unlock / Mi Unlock: статический аудит исходника

**Дата:** 10.10.2026. **Этап:** проектирование, НЕ реализация Android и НЕ аппаратное тестирование.

**База:** `NekoFlash-main-legacy.zip` из утверждённого checkpoint REV3, SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`. Другие проекты, NekoFlash Pro и удалённые репозитории не использовались.

## Фактические функции в оригинале

1. `MainActivity.kt:2331–2493`: вкладка `Unlock` строится из двух разделов **Mi-аккаунт** и **Загрузчик**. Вход через отдельную `MiLoginActivity`; после авторизации показываются ID, регион, dataCenterZone. Есть ручной выбор зоны и выход/смена аккаунта. Состояние загрузчика и возможность запуска зависят от Fastboot diagnostics.
2. `MainActivity.kt:1571–1622`: после web-авторизации получается unlockApi-сессия через `MiAccountClient.exchangeToken`; при неудаче вход не завершается успехом.
3. `DeviceViewModel.kt:885–990`: цепочка `getVar(product) → readXiaomiUnlockToken → nonce → checkClear → requestUnlock → stageAndOemUnlock`. Дальше **VERIFY_PENDING**, а не немедленный SUCCESS. Повторное подключение с `getvar:unlocked=yes` требуется для финального подтверждения.
4. `DeviceViewModel.kt:1336–1367,1451–1518`: pending-верификация сохраняет product и serial (если доступен), проверяет совпадение после reconnect, отклоняет несовпадающее устройство, маркирует success при `unlocked=yes`, failed при доказанном `no` и ждёт при отсутствии достоверного ответа.
5. `MiUnlockClient.kt`: взаимодействие с серверами Xiaomi требует живой авторизации и реального ответа Xiaomi; успешность такого API на конкретных моделях и действующие правила Xiaomi данным статическим аудитом **не подтверждаются**.

## Предлагаемый REV4: только Unlock, без лишних элементов

- Сохранить общую верхнюю USB-панель, терминал справа и пять нижних вкладок (третья — **Sideload**, по утверждённому REV3).
- Отдельно и компактно показать **Mi-аккаунт** (не авторизован → вход; авторизован → статус, раскрываемые параметры зоны и выход) и **Загрузчик** (фактический статус, условие Fastboot, предупреждение о стирании, основное действие).
- Если диагностикой доказано `unlocked=yes`, не предлагать повторный unlock.
- Управление регионом оставить в раскрываемых **параметрах аккаунта**, не превращать это в отдельную вкладку. Не добавлять `oem unlock`/сырые команды/ADB в Unlock; экспертный терминал отдельный.
- Визуально не изображать успешную разблокировку сразу после отправки. Итог — после достоверной проверки reconnect.
- Макет демонстрационный: никакой логин, серверный запрос и USB-команда не выполняются. `?preview=ready` показывает визуальный пример авторизованной сессии с Fastboot, а не реальное состояние устройства.

## Технические риски, которые нельзя потерять при будущем переносе

**P0 / корректность устройства:** не смешать физические устройства при переподключении; сопоставлять pending unlock и новое подключение по доступным достоверным идентификаторам. Не разрешать GUI запуск при занятом USB/операции. Не объявлять успешную разблокировку по ответу `oem unlock` без подтверждения `getvar:unlocked=yes`.

**P1 / итог операций:** проверить корректное завершение **того же** `OperationCenter`/истории/отчёта по финальному verdict. В legacy `verifyPendingUnlockIfReady` обновляет `_operationProgress`, но требуется регрессионное доказательство, что вся сохранённая операция и её отчёт получают такой же финальный статус. При истечении срока ожидающей проверки legacy удаляет маркер и пишет в лог, без явного итогового verdict для той же операции; в 2.0 это не должно превращаться в ложный success/fail.

**P1 / показ серверного предупреждения:** `runMiUnlockFromUi` передаёт `onClearInfo = { _, _ -> }`, поэтому живое сообщение серверной стадии `checkClear` о стирании/условиях не отображается в самостоятельном элементе UI, хотя логируется. Нужна безопасная обработка, не добавляющая лишних постоянных кнопок/окон. Предупреждение об удалении данных перед операцией обязательно видно.

**P1 / учёт состояния:** в legacy кнопка запуска зависит от Fastboot connection и занятости операции, а `miAuth` проверяется условием показа блока; перенос должен учитывать действительность сессии, фактическое состояние устройства и достоверность диагностики. Не использовать устаревший `unlocked`/старую USB-цель без проверки.

**P1 / секреты:** auth cookies, passToken, ssecurity, device token и server encryptData — конфиденциальные; не писать их в пользовательские экспортируемые логи/отчёты и не вводить в макет. Предусмотреть отзыв/сброс сессии и разграничение web login origin согласно исходному `MiAccountSecurityPolicy`.

**P2 / совместимость:** возможны изменения серверного протокола/политики Xiaomi; не обещать совместимость всех моделей или обход авторизации. Сохранить ясные сообщения об отказе Xiaomi (в том числе бизнес-код 20045 и ручную корректировку зоны при обоснованной необходимости).

**P2 / тестирование:** тесты на fake client/transport: успешная авторизация, expired session, wrong zone, server rejection, disconnect during stage, `VERIFY_PENDING`, reconnect same/wrong device, `getvar:unlocked=yes/no/unknown`, timeout, concurrent USB action. На пользовательском аппарате сначала только неразрушающая диагностика; выполнение unlock стирает данные и требует отдельного прямого решения владельца, даже если аппаратная проверка идёт на разблокированном POCO X3 Pro.

## Что не сделано

- Реальная авторизация в Xiaomi, проверка действующих требований сервиса, online запросы, unlock, тест на телефоне, сборка APK, редактирование Android-кода и GitHub.
- Проект **REV4 — предлагаемый**, НЕ утверждён пользователем и не заменяет утверждённый REV3.
