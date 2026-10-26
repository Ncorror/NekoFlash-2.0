#!/usr/bin/env python3
"""REV19 integration guards; JVM and native tests exercise behavior separately."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev18.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
main = (java / 'MainActivity.kt').read_text()
vm = (java / 'DeviceViewModel.kt').read_text()
adb = (java / 'AdbProtocol.kt').read_text()
native = (root / 'app/src/main/cpp/native_usbfs.cpp').read_text()
fastboot = (java / 'FastbootProtocol.kt').read_text()
assert '-s SERIAL is not used' not in main
assert 'val expectedSerial = request.serial' in main
handler = main.split('private fun handleFastbootTerminalCommand(')[1].split('private fun handleAdbTerminalCommand(')[0]
assert 'viewModel.runFastbootTerminalPlan(action.steps, expectedSerial)' in handler
assert handler.count('viewModel.runFastboot') == 1, 'Every terminal wire/payload route uses one serial-bound plan'
start = vm.split('private fun startOperation(')[1]
assert start.index('FastbootSerialSelection.matches') < start.index('context.block()')
assert 'sideloadGuiAttemptId != attemptId' in vm
sideload = vm.split('internal fun runSideload(source: FirmwareSource)')[1].split('private fun startOperation(')[0]
assert sideload.index('startOperation(') < sideload.index('persistPendingSideloadVerification(source)')
assert sideload.index('persistPendingSideloadVerification(source)') < sideload.index('proto.sideloadZip(')
assert 'pending.serial == null || pending.serial != currentUsbSerial()' in vm
assert '!pending.transferFinished' in vm
assert 'proto.inspectRecoveryInstallResult(pending.boundary)' in vm
assert 'RECOVERY_INSTALL_LOG_PATHS' not in adb
assert adb.count('boundary.matches(') == 2, 'Check log prefix/boot before and after reading'
assert 'NATIVE_USBFS_HARD_TIMEOUT_MS = 0' in fastboot
assert native.count('deadline.hard_expired(') == 4
assert 'deadline.stalled(current_ms)' in native
assert 'deadline.progress(last_progress_ms)' in native
assert 'control->cancelled.load(std::memory_order_acquire)' in native
assert 'PendingUrbOwnershipGuard pending_urb_guard(slots)' in native
assert 'discard_and_reap_all(usb_fd.get(), slots, &pending_count, &drain_errno)' in native
print('PASS REV19: serial selection, attempt-bound verdicts, progress-based native timeout, cancel/drain preserved')
