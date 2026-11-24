#!/usr/bin/env python3
"""REV27 repository guard: ADB redirect, host/device routing, terminal transcript selection, and regression wiring."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev26.py')], cwd=root, check=True)
java = root/'app/src/main/java/ru/forum/adbfastboottool'
main = (java/'MainActivity.kt').read_text()
vm = (java/'DeviceViewModel.kt').read_text()
adb = (java/'AdbProtocol.kt').read_text()
policy = (java/'AdbShellRedirectPolicy.kt').read_text()
publisher = (java/'AdbShellRedirectPublisher.kt').read_text()
human = (java/'HumanReadable.kt').read_text()

# Terminal: every shell-producing branch goes through one redirect-aware factory.
factory = main.split('private fun adbShellAction')[1].split('private fun parseAdbInstallMultiple')[0]
assert 'AdbShellRedirectPolicy.parse(' in factory
assert 'resolveTerminalOutputFile(' in factory, 'redirect target must resolve exactly like an adb pull destination'
parse_adb = main.split('private fun parseAdbCommand')[1].split('private fun adbShellAction')[0]
assert 'TerminalAction.AdbShell(' not in parse_adb, 'shell branches must use adbShellAction'
assert parse_adb.count('adbShellAction(') == 4
assert 'resolveTerminalOutputFile(tokens.getOrNull(2).orEmpty(), defaultName)' in parse_adb, 'adb pull path rules must stay untouched'
assert 'viewModel.runAdbShell(action.command, action.redirect)' in main
assert 'val redirect: AdbProtocol.ShellRedirect? = null' in main
assert 'redirect = redirect' in vm and 'redirect.target.name' in vm

# Protocol: stdout goes to the file, stderr keeps showing, evidence stays, publish happens once.
assert 'if (logOutput && redirect == null) ShellOutputTextRenderer(false, onLog)' in adb
assert adb.count('if (logOutput && redirect == null) ShellOutputTextRenderer(false, onLog)') == 2
assert 'if (logOutput) ShellOutputTextRenderer(true, onLog)' in adb, 'stderr must never be redirected'
assert adb.count('publishShellRedirect(') == 2  # call + definition
assert adb.count('AdbShellRedirectPublisher.publish(') == 1
assert adb.count('redirectProgress?.accept(data.size)') == 2
assert 'return redirect == null && startInteractiveShell()' in adb
assert 'catch (error: LocalFileCopy.NotEnoughSpace)' in adb and 'rawKeptLine(raw)' in adb

# Publisher: atomic replace like pull, bounded copy, source evidence is read-only.
assert 'FilePublication.replace(staged, target)' in publisher
assert 'LocalFileCopy.copy(' in publisher and '.copyTo(' not in publisher
assert 'source.delete' not in publisher and 'renameTo' not in publisher and 'Files.move' not in publisher
assert 'setLength(originalLength)' in publisher

# Keep the terminal's Cancel action explicit, localized and wired to cancellation.
import xml.etree.ElementTree as ET
ui = ET.parse(root/'app/src/main/res/layout/activity_main.xml').getroot()
android_ns = '{http://schemas.android.com/apk/res/android}'
stop = [node for node in ui.iter() if node.attrib.get(android_ns + 'id') == '@+id/btnTerminalStop']
assert len(stop) == 1 and stop[0].tag == 'com.google.android.material.button.MaterialButton'
assert stop[0].attrib.get(android_ns + 'text') == '@string/terminal_stop_button'
terminal_cancel = main.split('findViewById<View>(R.id.btnTerminalStop).setOnClickListener {', 1)[1].split('}', 1)[0]
assert 'if (terminalShowsLocalCommand) viewModel.cancelLocalTerminalCommand()' in terminal_cancel
assert 'else requestOperationCancelFromUi()' in terminal_cancel
# Explicit command routing keeps host shell, ADB, and Fastboot separate.
command_input = main.split('private fun handleTerminalCommandInput', 1)[1].split('private fun handleInteractiveAdbShellInput', 1)[0]
routing_path = java/'TerminalCommandRoutingPolicy.kt'
routing = routing_path.read_text()
assert 'TerminalCommandRoutingPolicy.resolve(' in command_input
assert 'Route.Local -> runLocalTerminal(raw, route.command)' in command_input
assert 'Route.Adb -> handleAdbTerminalCommand(route.command)' in command_input
assert 'Route.Fastboot -> handleFastbootTerminalCommand(route.command)' in command_input
assert 'Route.FastbootShell -> runLocalTerminal(raw, route.command)' in command_input
assert 'if (explicitLocal)' in routing and 'localCommand ?: clean.removePrefix("local").trimStart()' in routing
assert 'if (implicitLocalCommand != null) return Route.Local(implicitLocalCommand)' in routing
assert 'if (explicitAdb) {' in routing and 'Route.Adb(explicitCommand)' in routing
assert 'Route.InteractiveAdb(clean, "adb", explicitCommand)' in routing
assert 'INTERACTIVE_ADB_COMMANDS' in routing
assert 'if (explicitFastboot)' in routing and 'Route.FastbootShell("sh -c ${quoteForShellArgument(clean)}")' in routing
assert 'if (explicitHostShell) return Route.Local(clean)' in routing, 'sh/bash must never inherit a selected ADB/Fastboot tab'
assert 'if (interactiveAdbShellActive)' in routing
assert 'currentContext == "adb" && firstCommand in adbDeviceDiagnostics' in routing, 'ADB diagnostics must not be injected into an interactive shell'
local_shell = (java/'LocalShell.kt').read_text()
assert 'private fun hasUnsupportedShellSyntax' in local_shell
assert 'if (extractShellInvocation(line) != null) return null' in local_shell
assert 'if (extractShellInvocation(line) != null) return true' in local_shell
assert 'val shellArgs = if (requiresSystemShellEvaluation(shellInvocation.command)) {' in local_shell
assert 'listOf("-c", shellInvocation.command)' in local_shell, 'shell operators and command substitution must preserve the original command text'
assert 'tokenize(shellInvocation.command).drop(1).map { it.value }' in local_shell, 'plain script paths and arguments must be passed directly to the configured shell'
assert "character == '$' || character == '`' -> return true" in local_shell, 'quoted shell expansions must retain shell evaluation'
routing_tests = (root/'app/src/test/java/ru/forum/adbfastboottool/TerminalCommandRoutingPolicyTest.kt').read_text()
assert 'Route.Local("bash $(ls)")' in routing_tests
assert 'resolve("local bash $(ls)", "fastboot", false)' in routing_tests
assert 'Route.Adb("shell bash $(ls)")' in routing_tests
assert 'Route.FastbootShell' in routing_tests
copy_ui = main.split('private fun copyTerminalOutput()', 1)[1].split('private fun showCurrentSessionSummaryDialog()', 1)[0]
assert 'TerminalTranscript.plainText(viewModel.terminalSnapshot())' in copy_ui
transcript = (java/'TerminalTranscript.kt').read_text()
assert 'ansiControlSequence.replace(it, "")' in transcript and 'lines.joinToString("\\n")' in transcript
assert 'setTextIsSelectable(true)' in copy_ui
assert 'terminal_transcript_select_hint' in copy_ui and 'setView(content)' in copy_ui
assert 'showSelectableTerminalTranscript()' in main and 'setOnLongClickListener' in main
assert 'System shell is off by default' in (java/'LocalShell.kt').read_text()
usb_cancel = main.split('private fun requestOperationCancelFromUi()', 1)[1].split('private fun ', 1)[0]
assert 'viewModel.cancelActiveOperation()' in usb_cancel and 'cancelLocalTerminalCommand' not in usb_cancel

# Pure policy: no Android types, no I/O.
assert 'import android' not in policy and 'FileOutputStream' not in policy and 'readText' not in policy
assert 'import android' not in human

# Android's string parser treats unescaped apostrophes as quote delimiters. In a
# string-array this can poison AAPT's error reporter and surface as an NPE rather
# than a useful resource diagnostic. Validate every text-bearing values item.
def has_unescaped_android_apostrophe(value):
    in_double_quote = False
    index = 0
    while index < len(value):
        char = value[index]
        if char == '\\':
            index += 2
            continue
        if char == '"':
            in_double_quote = not in_double_quote
        elif char == "'" and not in_double_quote:
            return True
        index += 1
    return False

for values_file in (root/'app/src/main/res').glob('values*/*.xml'):
    values_root = ET.parse(values_file).getroot()
    for resource in values_root:
        if resource.tag == 'string' or (resource.tag == 'item' and resource.attrib.get('type') == 'string'):
            candidates = [resource]
        elif resource.tag in ('string-array', 'array', 'plurals'):
            candidates = [child for child in resource if child.tag == 'item']
        else:
            continue
        for index, candidate in enumerate(candidates):
            value = ''.join(candidate.itertext())
            assert not has_unescaped_android_apostrophe(value), (
                f'unescaped apostrophe in {values_file}:{resource.attrib.get("name", "<unnamed>")}'
                f' item {index + 1}: {value!r}'
            )

subprocess.run(['python3', str(root/'tests/check_terminal_welcome.py')], cwd=root, check=True)
