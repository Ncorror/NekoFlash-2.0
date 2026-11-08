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


## CI #496 — первая гипотеза отвергнута

Попытка применить `Resources.updateConfiguration` только к `instrumentation.targetContext.resources` **не сработала на API26**: установленный UI-тест продолжил показывать `config.fontScale=1.0` и `scaledDensity/density=1.0` через 10 секунд. Это доказывает, что `MainActivity` в данной среде получает отдельную `ResourcesImpl`, не использующую это изменение. CI #496 упал на реальном невыполненном предусловии (27/28), не объявлялся зелёным.

## REV55b — исправленный тестовый механизм

Добавлен **только инструментальный** `NekoFlashAndroidTestRunner` (наследник штатного `AndroidJUnitRunner`), зарегистрирован в `app/build.gradle` и в единственном вызове `am instrument` внутри `.github/workflows/build.yml`. В отличие от первой гипотезы, его `callActivityOnCreate()` получает **конкретную Activity после attach, но до onCreate/inflate** и выставляет `fontScale=1.3` в её реальных ресурсах. Флаг включается только внутри теста, исключительно на API26; все другие тесты и Activity получают неизменённые настройки. При пересоздании после поворота hook применяется повторно. В `IdentityHashMap<Resources,Configuration>` запоминаются оригиналы всех затронутых ресурсов и они восстанавливаются в `finally`.

Приёмка усилена: `waitForLargeFontScale()` теперь вызывается до начала операции, после поворота и после явного recreation; на API26 проверяются и `Configuration.fontScale`, и `scaledDensity/density`. Все проверки прокрутки, доступности кнопки отчётов, pending cancel, снимки и остальные 27 тестов сохранены. Никаких пустых обходов, skip, изменения таймаута либо подмены результатов.

**Этот механизм пока лишь кандидат.** Его действительное действие необходимо подтвердить полным CI (включая эмуляторный API26). Не менять `main` и не закрывать #16/#57 при красных результатах.


## Итог приёмки и интеграции (обновлено 8 ноября 2026)

- REV55b на SHA `8dd250407bdc328201f5aebbf2778d44c5a091c1`: [CI #501](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42426015449) полностью **PASS** — 336/336 JVM, lint без замечаний, Android API26 28/28, Android API34 28/28, комплект тестировщика. **Независимый повтор API26 на том же SHA тоже 28/28 PASS** (вторая попытка CI #501).
- [PR #59](https://github.com/Ncorror/NekoFlash-2.0/pull/59) **слит** в `main` обычным merge-коммитом [`efdc4a96e479`](https://github.com/Ncorror/NekoFlash-2.0/commit/efdc4a96e479d360a57a8b7dbba49d2ff905af13). Предыдущий `main` сохранён в `backup/main-before-rev55-ci-20261108`. Ни один production Kotlin/ADB/USB/Fastboot файл этим PR не изменён.
- [Послемержевый CI #502](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/42427111327) — **PASS** по точному merge SHA: build, Android API26, Android API34, пакет Insiders и pinning завершились успешно. Это независимая от PR CI контрольная точка. Issue #16 также содержит независимую историческую нестабильность других UI-тестов; не закрывать её целиком только по одному исправленному предусловию.

Предыдущие формулировки «кандидат» выше оставлены как хронология первоначального исследования и ошибок первых попыток; текущий статус указан в этом разделе.
