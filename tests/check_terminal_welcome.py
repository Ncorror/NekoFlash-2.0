#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
vm = (java / 'DeviceViewModel.kt').read_text(encoding='utf-8')
welcome = (java / 'TerminalWelcomeMessage.kt').read_text(encoding='utf-8')
adapter = (java / 'ConsoleLogAdapter.kt').read_text(encoding='utf-8')
activity = (java / 'MainActivity.kt').read_text(encoding='utf-8')
dock = (java / 'ConsoleDockController.kt').read_text(encoding='utf-8')

assert 'TerminalWelcomeMessage.build(application, deviceBinariesEnabled = localShell.isDeviceBinariesEnabled).forEach(::append)' in vm
assert 'deviceBinariesEnabled = localShell.isDeviceBinariesEnabled' in vm
assert 'putBoolean("device_binaries_enabled", enabled)' in vm
assert 'sharedWorkspace = publicWorkspace?.let(::normalizeFile)' in (java / 'LocalShell.kt').read_text(encoding='utf-8')
assert 'Files.createSymbolicLink(alias.toPath(), shared.toPath())' in (java / 'LocalShell.kt').read_text(encoding='utf-8')
assert 'private val shellExecutable: File = File("/system/bin/sh")' in (java / 'LocalShell.kt').read_text(encoding='utf-8')
local_shell_source = (java / 'LocalShell.kt').read_text(encoding='utf-8')
assert 'writeExecutable("bash", "#!$shellPath' in local_shell_source
assert 'writeExecutable("bash"' in local_shell_source and 'exec ' in local_shell_source and 'shellPath' in local_shell_source
assert 'val containsStagingPath = normalized.any' in (java / 'LocalShell.kt').read_text(encoding='utf-8')
assert 'internal fun submitLocalTerminalScriptCommand(' in vm
routing = (java / 'TerminalCommandRoutingPolicy.kt').read_text(encoding='utf-8')
assert 'if (explicitLocal)' in routing and 'localCommand ?: clean.removePrefix("local").trimStart()' in routing
assert 'if (implicitLocalCommand != null) return Route.Local(implicitLocalCommand)' in routing
assert routing.index('if (interactiveAdbShellActive)') < routing.index('if (implicitLocalCommand != null)')
assert 'if (explicitHostShell) return Route.Local(clean)' in routing
assert 'Route.FastbootShell("sh -c ${quoteForShellArgument(clean)}")' in routing
assert 'interactiveAdbShellActive' in routing
assert 'requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT' in vm
assert 'prepareFastbootSession = false' in vm
assert 'FastbootShellBridge' in vm
assert 'FastbootVbmeta.createPatchedCopy(' in vm
assert 'values[index] == "--disable-verity"' in vm
assert 'values[index] == "--disable-verification"' in vm
assert 'Files.createTempFile(temporaryDirectory.toPath(), "nekoflash-vbmeta-", ".img")' in (java / 'FastbootVbmeta.kt').read_text(encoding='utf-8')
assert 'ProcessBuilder(command)' in local_shell_source
assert 'env["TMPDIR"] = temporaryDirectory.absolutePath' in local_shell_source
assert 'env["NEKOFLASH_FASTBOOT_TOKEN"] = it.authToken' in local_shell_source
assert 'private const val WIDTH = 48' in welcome
assert 'centered("✨ ${details.title}")' in welcome
assert 'displayWidth(line) == WIDTH' in welcome
assert 'takeToDisplayWidth(value, CONTENT_WIDTH)' in welcome
assert all(emoji in welcome for emoji in ('📱', '🧩', '📁', '🕒', '📅', '⌂', '↗', '⚙', '↩', '🔗', '▣', '🔐', '💡', '🔄'))
assert 'value.startsWith("📱")' in adapter and 'value.startsWith("💡")' in adapter
assert 'value.startsWith("🟢")' in adapter and 'value.startsWith("🔴")' in adapter
assert 'centered("help")' in welcome
assert 'local ls' not in welcome and 'local cat' not in welcome and 'local grep' not in welcome
assert 'TerminalWelcomeMessage.isWelcomeLine(item.text)' in adapter
assert 'textSizeSp)' in adapter
assert 'var textSizeSp = initialTextSizeSp' in adapter
assert 'fun setTextSizeSp(' not in adapter, 'Kotlin property setter already generates setTextSizeSp(Float)'
assert 'TerminalWelcomeMessage.TEXT_SIZE_SP else textSizeSp' in adapter
assert 'textView.setSingleLine(isWelcome)' in adapter
assert 'TextUtils.TruncateAt.END' in adapter
assert 'stackFromEnd = false' in activity
assert 'pinToTop = showWelcome' in activity and 'pinToBottom = !showWelcome' in activity
assert 'pinToTop -> if (!isFinishing && !isDestroyed && consoleDockController.isExpanded)' in activity
assert 'layoutManager?.scrollToPositionWithOffset(0, 0)' in activity
assert 'scrollOutputToBottom' not in dock
assert 'doOnNextLayout' not in activity

keys = {
    'terminal_welcome_title', 'terminal_welcome_subtitle',
    'terminal_welcome_environment', 'terminal_welcome_host_label',
    'terminal_welcome_binary_label', 'terminal_welcome_binary_enabled', 'terminal_welcome_binary_disabled',
    'terminal_welcome_host_value', 'terminal_welcome_abi_label',
    'terminal_welcome_workspace_label', 'terminal_welcome_workspace_value',
    'terminal_welcome_time_label', 'terminal_welcome_date_label',
    'terminal_welcome_command_source', 'terminal_welcome_host_route',
    'terminal_welcome_adb_route', 'terminal_welcome_fastboot_route',
    'terminal_welcome_local_alias', 'terminal_welcome_mixed_fastboot',
    'terminal_welcome_system_shell', 'terminal_welcome_enable_command',
    'terminal_welcome_disable_command', 'terminal_welcome_help',
    'terminal_welcome_help_hint', 'terminal_welcome_transport',
    'terminal_welcome_access_label', 'terminal_welcome_access_value',
}
for locale in ('values', 'values-ru'):
    strings = ET.parse(root / f'app/src/main/res/{locale}/strings.xml').getroot()
    found = [item.attrib['name'] for item in strings.findall('string')]
    missing = keys - set(found)
    assert not missing, f'{locale}: missing terminal welcome resources: {sorted(missing)}'
    assert len(found) == len(set(found)), f'{locale}: duplicate string resource name'
    ready = strings.find("string[@name='system_terminal_ready']")
    assert ready is not None and '\n' not in (ready.text or ''), f'{locale}: stale multiline terminal banner'

print('PASS terminal: universal HOME, persistent device-binary state, shell bridge, foreground Fastboot script operation, welcome and EN/RU resources')
