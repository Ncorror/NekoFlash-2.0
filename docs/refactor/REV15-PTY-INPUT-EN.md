# REV15 — real PTY input for adb shell (EN)

## Goal

Meet the NekoFlash 2.0 terminal contract: HOST CLI remains a normal line editor, while **only explicit `adb shell`** uses PTY and receives input as the user types.

## Implementation

- Interactive ADB still opens `shell,v2,pty:` (or legacy `shell:` when shell_v2 is unavailable).
- One-shot `adb shell <command>` remains `shell,v2,raw:<command>`; PTY is not forced for normal host commands.
- New `PtyInputEditText` intercepts Android InputConnection only while an interactive shell is actually active.
- Commit/paste is forwarded as UTF-8 without copying raw input into the host diagnostic log.
- IME composition uses a delta policy so `h -> he -> hel` does not resend previous characters.
- Backspace emits DEL (0x7f), Enter emits LF, Tab emits TAB, arrow keys emit ANSI escape sequences.
- History ↑/↓ buttons become remote shell arrows only while PTY is active; outside PTY they retain HOST CLI history.
- Send becomes Enter in PTY and remains normal `handleCommandInput()` outside PTY.
- Android `adjustResize` remains enabled for the keyboard.

## Privacy

Raw PTY bytes are deliberately not logged on the host, protecting password-style prompts where remote echo may be disabled.

## Tests

- JVM `PtyInputDeltaTest` covers composition append/replace/delete and UTF-8 paste.
- REV15 structural checks enforce explicit PTY vs raw one-shot shell, InputConnection behavior, paste/DEL/Enter/arrows and no raw-input logging.
- Android lint/build remains mandatory.

## Device gate

Non-destructive test: open `adb shell`, type slowly and verify immediate echo, Backspace, paste, ↑/↓ remote history, IME resize, then exit and confirm HOST CLI returns to normal line editing.

REV15 is software-ready only after CI; it is not hardware-verified until this test passes.
