#!/usr/bin/env python3
"""REV15: explicit adb-shell PTY gets raw IME/paste input; HOST CLI stays line based."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev14.py')], cwd=root, check=True)

jvm = root/'app/src/main/java/ru/forum/adbfastboottool'
adb = (jvm/'AdbProtocol.kt').read_text()
vm = (jvm/'DeviceViewModel.kt').read_text()
main = (jvm/'MainActivity.kt').read_text()
widget = (jvm/'PtyInputEditText.kt').read_text()
delta = (jvm/'PtyInputDelta.kt').read_text()
manifest = (root/'app/src/main/AndroidManifest.xml').read_text()
layout = ET.parse(root/'app/src/main/res/layout/activity_main.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'

# PTY only for the explicit interactive shell. One-shot host commands remain raw/non-PTY.
assert 'val service = if (useShellV2) "shell,v2,pty:" else "shell:"' in adb
assert 'val service = "shell,v2,raw:$command"' in adb
assert 'fun sendInteractiveShellBytes(payload: ByteArray): Boolean' in adb
raw_block = adb.split('fun sendInteractiveShellBytes(payload: ByteArray): Boolean')[1].split('fun sendInteractiveShellInput')[0]
assert 'onLog(' not in raw_block, 'Raw PTY bytes must never be copied into host logs'

# Android IME/paste bridge: committed UTF-8, composition delta, DEL, Enter and arrows.
assert 'class PtyInputEditText' in widget
assert 'InputConnectionWrapper' in widget
assert 'override fun setComposingText' in widget
assert 'PtyInputDelta.rewrite(composing, next)' in widget
assert 'override fun commitText' in widget
assert 'override fun deleteSurroundingText' in widget
assert 'android.R.id.paste' in widget
assert '"\\u001B[A"' in widget and '"\\u001B[B"' in widget
assert "byteArrayOf('\\n'.code.toByte())" in widget
assert 'const val DEL: Int = 0x7f' in delta

# HOST CLI and raw PTY share the UI, but mode selection is explicit and dynamic.
assert 'private lateinit var etCommand: PtyInputEditText' in main
assert 'etCommand.configurePty(' in main
assert 'viewModel.isInteractiveAdbShellActive()' in main
assert 'viewModel.sendInteractiveAdbShellBytes(bytes)' in main
assert 'handleCommandInput()' in main
assert 'fun sendInteractiveAdbShellBytes(payload: ByteArray): Boolean' in vm
assert 'android:windowSoftInputMode="adjustResize"' in manifest

input_nodes = [x for x in layout.iter() if x.attrib.get(a+'id') == '@+id/etCommand']
assert len(input_nodes) == 1
assert input_nodes[0].tag.endswith('PtyInputEditText')

assert (root/'app/src/test/java/ru/forum/adbfastboottool/PtyInputDeltaTest.kt').is_file()
print('PASS REV15: raw PTY IME/paste/backspace/enter, HOST CLI preserved, no raw-input logging')
