# REV55 — стабильная проверка крупного шрифта и Operation Center на Android 8 (Issue #16)

Дата: 2026-11-08. Эта ветка создана после аппаратно принятого фикса USB ZLP REV54, коммит `main=ddf1fdd156f1138784fe4b49b4918764bb386914`.

## Наблюдение

Постмержевый CI #495, включая повтор строго того же SHA, дважды показал API26 27/28 с единственным отказом `OperationCenterAndroidTest.longDetailsScrollWithLargeTextInLandscapeAndCancellationStaysPending` на исходной строке 216:

```
shell("settings put system font_scale 1.3")
ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    waitUi(scenario) { it.resources.configuration.fontScale >= 1.29f }
```

После 10 секунд Activity по-прежнему не показывала новое значение `Configuration.fontScale`; тест даже не дошёл до `begin()`, открытия диалога, поворота или скролла. В диагностике `sheet=false, viewport=0x0` — **следствие не выполненного предусловия**, а не доказательство сломанной рабочей панели.

## Обоснование

AndroidX `AndroidFontScaleHelper` (https://android.googlesource.com/platform/frameworks/support/+/HEAD/testutils/testutils-runtime/src/main/java/androidx/testutils/AndroidFontScaleHelper.kt) **не применяет системный font_scale на API <29**, вместо этого пропускает такие тесты по ограничениям платформы. Мы **не хотим пропускать тест на API26**: он важен для нашей совместимости.

В тестовом (не production) коде для API26 вместо системной настройки используется локальное `Resources.updateConfiguration` с `fontScale=1.3`, **до создания MainActivity и инфляции её UI**. Для Android 10+ системный способ не меняется. Исходная конфигурация Resources сохраняется и восстанавливается в `finally` после закрытия ActivityScenario, включая путь падения.

Для API26 дополнительная строгая проверка требует одновременно `configuration.fontScale >= 1.29` **и** фактического `displayMetrics.scaledDensity / density >= 1.29`. Поэтому изменение `Configuration` без применения масштаба к шрифтам не засчитывается. Сохранились все прежние проверки открытого диалога, ориентации, прокрутки 18 строк, восстановления позиции после recreation, и запрета ложного завершения операции при отмене. Никакой тест не отключён, число UI-тестов не уменьшено, таймаут не повышен и основное приложение не изменено.

## Контроль

- CI: API26 28/28, API34 28/28, JVM, lint, сборка и пакет тестировщика должны PASS.
- При обнаружении, что `Resources.updateConfiguration` не удерживается после recreation на Android 8, менять **только тестовый подход**, но не ослаблять проверки поведения приложения.
- Данные и журналы пользователя не трогать. USB/ZLP реализация REV54 и `main` должны остаться без новых изменений.
- Issue #16 закрывать только когда проблема действительно устранена, предпочтительно подтверждена повторным независимым прогоном; Issue #57 до общего зелёного CI оставлять открытой.

Статус: кандидат до CI, без заявления об успешной проверке.
