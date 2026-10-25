#!/usr/bin/env python3
"""REV20 wiring contracts; coroutine, persistence and Android lifecycle behavior run in JVM CI."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev19.py')], cwd=root, check=True)
jvm = root / 'app/src/main/java/ru/forum/adbfastboottool'
vm = (jvm / 'DeviceViewModel.kt').read_text()
main = (jvm / 'MainActivity.kt').read_text()
service = (jvm / 'FlashOperationService.kt').read_text()
session = (jvm / 'DeviceSessionStore.kt').read_text()
task = (jvm / 'ServiceOwnedOperation.kt').read_text()
assert 'viewModelScope' not in vm
assert 'DeviceSessionStore.attachUi(application)' in main
assert 'DeviceSessionStore.detachUi()' in main
assert 'ViewModelProvider(this)[DeviceViewModel::class.java]' not in main
assert 'ACTIVITY_DESTROY", terminal = false' in main
assert 'CoroutineStart.LAZY' in task
assert 'job.invokeOnCompletion' in task
assert service.index('ServiceCompat.startForeground(') < service.index('task.acknowledgeForeground()')
assert 'task.rejectStartup(' in service and 'START_NOT_STICKY' in service
assert 'private const val EXTRA_OPERATION_ID' in service
assert 'registry.remove(id)' in service
assert 'serviceOperation?.finished?.await()' in vm
assert 'awaitNativeUsbfsIdle()' in vm
assert 'recoveryStore.writeOperation(running)' in vm
assert vm.index('recoveryStore.writeOperation(running)') < vm.index('context.block()')
assert 'recoveryStore.writeDraft(draft)' in vm
assert 'Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP' in service
assert '.setSilent(true)' in service and 'rev20_notification_open' in service
assert 'ACTION_USB_DEVICE_DETACHED' in session and 'session.disconnectCurrent()' in session
a = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
entry = next(x for x in manifest.findall('./application/service') if x.get(a + 'name') == '.FlashOperationService')
assert entry.get(a + 'foregroundServiceType') == 'connectedDevice'
assert entry.get(a + 'stopWithTask') == 'false'
assert entry.get(a + 'exported') == 'false'
permissions = {x.get(a + 'name') for x in manifest.findall('uses-permission')}
assert 'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE' in permissions
assert 'android.permission.FOREGROUND_SERVICE_DATA_SYNC' not in permissions
for name in ('ServiceOwnedOperationTest', 'OperationRecoveryStoreTest', 'ForegroundServiceLifecycleTest'):
    assert (root / f'app/src/test/java/ru/forum/adbfastboottool/{name}.kt').is_file()
assert '[IQ-HARRY7](https://github.com/IQ-HARRY7)' in (root / 'README.md').read_text()
print('PASS REV20: service admission, screen-independent session, durable status/draft, no replay, token-aware stop, developer credit')
