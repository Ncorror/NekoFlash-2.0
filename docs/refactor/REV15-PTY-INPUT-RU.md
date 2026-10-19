# REV15 — настоящий PTY-ввод для adb shell (RU)

## Цель

Закрыть требование NekoFlash 2.0: HOST CLI остаётся обычным построчным терминалом, а **только явный `adb shell`** использует PTY и получает ввод по мере набора.

## Реализация

- Интерактивный ADB по-прежнему открывает `shell,v2,pty:` (или legacy `shell:` без shell_v2).
- One-shot `adb shell <команда>` остаётся `shell,v2,raw:<команда>`, то есть PTY не включается искусственно для обычных команд.
- Новый `PtyInputEditText` перехватывает Android InputConnection **только пока реально активен interactive shell**.
- Commit/paste отправляются как UTF-8 без добавления скрытого текста в host log.
- IME composing обрабатывается дельтой: повторное `h -> he -> hel` не пересылает уже отправленные символы.
- Backspace -> DEL (0x7f), Enter -> LF, Tab -> TAB, аппаратные/экранные стрелки -> ANSI ESC sequences.
- Кнопки истории ↑/↓ во время PTY отправляют стрелки в удалённый shell; вне PTY продолжают листать историю HOST CLI.
- Кнопка Send во время PTY отправляет Enter; вне PTY выполняет прежний `handleCommandInput()`.
- `adjustResize` сохранён, поэтому IME не должен перекрывать нижнюю строку.

## Приватность

Raw PTY bytes не логируются на хосте. Это важно для команд, которые отключают echo и запрашивают пароль/секрет. Старый line fallback сохраняет прежнее логирование команды; его нужно использовать только как совместимость.

## Проверки

- Pure JVM тесты `PtyInputDeltaTest`: composition append/replace/delete, UTF-8 paste.
- Structural REV15: explicit PTY vs raw one-shot shell, InputConnection, DEL/Enter/arrows/paste, отсутствие raw-input logging.
- Полный Android build/lint после каждого изменения.

## Аппаратный test gate

На тестовом телефоне проверить без destructive действий:

1. Открыть Terminal -> `adb shell`.
2. Ввести `echo 123` медленно по символам — удалённый PTY должен echo каждый символ, не ждать Send.
3. Backspace внутри строки.
4. Вставить `printf 'paste-ok\\n'`.
5. Стрелки ↑/↓ должны работать как history удалённого shell.
6. Ctrl-C/Ctrl-D остаются через существующие команды/кнопки.
7. Развернуть/свернуть клавиатуру и проверить сохранение высоты terminal.
8. Закрыть shell и убедиться, что HOST CLI снова редактирует строку локально.

До аппаратной проверки REV15 — software-ready, не hardware-verified.
