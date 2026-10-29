#!/usr/bin/env python3
"""REV26 wiring guards. JVM and installed-app tests cover behavior and cleanup."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev25.py')], cwd=root, check=True)
java = root/'app/src/main/java/ru/forum/adbfastboottool'
main = (java/'MainActivity.kt').read_text()
vm = (java/'DeviceViewModel.kt').read_text()
adb = (java/'AdbProtocol.kt').read_text()
extract = (java/'PackageArchiveExtractor.kt').read_text()
copy = (java/'LocalFileCopy.kt').read_text()
importer = (java/'WorkspaceFileImporter.kt').read_text()
observer = main.split('viewModel.operationActive.observe(this)')[1].split('viewModel.operationSteps.observe(this)')[0]
assert 'consoleDockController.collapse()' not in observer
assert 'withTerminalCommand { handleTerminalCommandInput() }' in main
assert 'viewModel.terminalLines.observe(this)' in main
assert 'R.id.btnConsoleClose' in main and 'R.id.btnTerminalStop' in main
assert 'workspaceImport.isBusy' in vm and 'workspaceImport.close()' in vm
assert 'PackageArchiveExtractor.extract' in adb
assert 'throw cancelled' in adb.split('private fun installPackageArchive')[1].split('private fun checkArchiveCancellation')[0]
assert 'Math.addExact' in extract and 'item.entry.crc' in extract
assert 'CRC32()' in copy and 'checkCancelled()' in copy and 'requireSpace' in copy
assert '.copyTo(' not in copy and '.copyTo(' not in extract
assert 'FilePublication.publishNew(staged, target)' in importer
assert 'cancellation.commit' in importer and 'channel.tryLock()' in importer
assert 'target.delete()' not in importer
print('PASS REV26: independent terminal, explicit close/stop, retained import, checked extraction/publication')
