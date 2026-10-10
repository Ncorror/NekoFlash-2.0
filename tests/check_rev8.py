#!/usr/bin/env python3
"""REV8 lossless diagnostic storage contracts; run after every PR push."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev7.py')], cwd=root, check=True)
store = (root/'app/src/main/java/ru/forum/adbfastboottool/DiagnosticLogStore.kt').read_text()
share = (root/'app/src/main/java/ru/forum/adbfastboottool/SanitizedLogShare.kt').read_text()
vm = (root/'app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt').read_text()
assert 'oldest.delete()' not in store, 'History segments must not be deleted'
assert 'pruneDirectory(logsDir)' not in store, 'Startup must not silently prune history'
assert 'Never delete historical logs' in store
assert 'const val DEFAULT_MAX_SOURCE_BYTES: Long = Long.MAX_VALUE' in share
assert 'reader.forEachLine { line ->' in share, 'Sanitized export must stream'
assert 'notifyLogStorageFailure(error)' in vm
assert (root/'app/src/test/java/ru/forum/adbfastboottool/DiagnosticLogStoreTest.kt').is_file()
print('PASS REV8: no silent diagnostic deletion, streamed export, storage-failure signal')
