#!/usr/bin/env python3
"""Metadata/permission separation and shared-source wiring; runtime checks cover real FDs."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev23.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
vm = (java / 'DeviceViewModel.kt').read_text()
main = (java / 'MainActivity.kt').read_text()
selection = (java / 'FirmwareSelection.kt').read_text()
store = (java / 'OperationRecoveryStore.kt').read_text()
assert 'private var quickFlashSource: FirmwareSource?' not in main
assert 'viewModel.quickFlashSource.observe(this)' in main
assert 'viewModel.refreshQuickFlashSource()' in main
assert 'quick-source.properties' in store
assert 'opened.readFullyAt(opened.sizeBytes - 1, probe)' in selection
assert 'actual != expectedSizeBytes' in selection
assert 'descriptor' not in store and 'takePersistableUriPermission' not in store
assert 'registerMassImageLauncher()' in main
assert 'outState.putString("mass_picker_partition", pendingMassPartition)' in main
assert 'pendingQueueSources[target] != generation' in vm
assert 'generation != quickSourceGeneration' in vm
queue = vm.split('private fun runFlashQueue(')[1].split('private fun formatBytesShort(')[0]
assert queue.index('item.selection.verify(') < queue.index('flashImage(proto, item.partition, item.selection.source()')
assert 'expectedSizeBytes = item.selection.expectedSizeBytes' in queue
assert 'queueExecute.isEnabled = draft.items.isNotEmpty() && sources.pending.isEmpty()' in main
assert '.copyTo(' not in selection
assert (root / 'app/src/androidTest/AndroidManifest.xml').is_file()
assert 'FirmwareFixtureProvider' not in (root / 'app/src/main/AndroidManifest.xml').read_text()
for name in ('FirmwareSelectionTest', 'FirmwareSelectionLifecycleTest'):
    assert (root / f'app/src/test/java/ru/forum/adbfastboottool/{name}.kt').is_file()
for name in ('build.yml', 'release.yml'):
    assert 'tests/check_rev24.py' in (root / '.github/workflows' / name).read_text()
assert 'OK (10 tests)' in (root / '.github/workflows/build.yml').read_text()
print('PASS REV24: durable checked Quick source, mixed File/SAF queue, stale-result guards, no automatic replay')
