# REV38 — Gboard / Android IME и интерактивный PTY

Дата: 31.10.2026. Основа: DEV после REV37 `d50d980f554eadd7423966b3a94f157f6885784c`.

## Причина

`PtyInputEditText` пересылал `setComposingText` немедленно в ADB PTY, но
`deleteSurroundingText{InCodePoints}` при любом удалении обнулял локальное
`composing`. Следующая правка слова через Gboard отправляла оставшуюся часть
вторично (например, `прив` → Backspace → `при` → дублирование `при`).
При `beforeLength > 0` и `afterLength > 0` обрабатывалась только первая
сторона курсора.

В Unicode `deleteSurroundingText` считает UTF-16 code units, а
`deleteSurroundingTextInCodePoints` — code points. Старое прямое использование
`beforeLength` могло отправить два DEL для одного emoji.

## Изменения

- `PtyInputDelta.deletion` вычисляет оставшуюся композицию и точные
  последовательности Backspace/CSI Delete с учётом UTF-16 surrogate pairs.
- Состояние композиции хранится в `PtyInputEditText`, доступно одновременно
  InputConnection и контекстному меню вставки. Сбрасывается при явной
  реконфигурации PTY, смене raw/host режима, успешной вставке, Enter и навигации.
- При отказе отправки по `sink` состояние не продвигается. Нет
  автоматического повторного исполнения потенциально опасных команд.
- Существующий HOST CLI сохраняет обычный `EditText`; в raw PTY байты
  пересылаются без host-side эха и без записи паролей в журнал.

## Проверки

- JVM: составной ввод кириллицы, удаление внутри композиции, обе стороны
  курсора, UTF-16 и code-point emoji, выход за композицию, no-op и
  некорректные длины.
- Новый Android-инструментальный тест без USB: реальные методы
  `InputConnection.setComposingText`, `deleteSurroundingTextInCodePoints`,
  `commitText`, `deleteSurroundingText`, `performEditorAction`,
  `sendKeyEvent`, а также меню Paste с синтетическим буфером обмена.
- В `build.yml` ожидается **20** инструментальных тестов вместо 19.
- Не меняются ADB / Fastboot wire, размеры пакетов REV37, flash, файлы
  диагностики и существующие функции терминала.

## Не закрыто

Нужна аппаратная проверка с Gboard, SwiftKey и другими IME на настоящем
Android HOST/TARGET, особенно управление курсором **в середине** длинной
композиции, ZWJ-графемы, удаление в удалённой shell со специфичным
line discipline, paste большого текста, поворот и USB-detach.
Юнит-тесты считают Unicode code points; реальные терминалы могут стирать
графемные кластеры по-своему. Успешный CI не равен проверке на устройствах.

Защиту репозитория и выпуск Release оставляем на финальный этап. `main`
не изменяется.
