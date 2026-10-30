#!/usr/bin/env python3
"""REV12: direct seekable SAF Sideload, strict USB and bilingual UI contracts."""
from pathlib import Path
import hashlib
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev11.py')], cwd=root, check=True)

base = root/'app/src/main/java/ru/forum/adbfastboottool'
adb_file = base/'AdbProtocol.kt'
adb = adb_file.read_text()
source = (base/'FirmwareSource.kt').read_text()
vm = (base/'DeviceViewModel.kt').read_text()
main = (base/'MainActivity.kt').read_text()

# REV18 reviewed receive-file, listing and unique-block progress fixes.
# Original USB transport and direct SAF expectations remain guarded.
data = adb_file.read_bytes()
git_blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
# Two byte-exact, audited variants only. REV31 adds disk-backed shell/v2
# and legacy output recording; the original SAF/sideload transport invariants
# remain covered by the rest of this and the older source guards.
assert git_blob in {
    '7acf241d15e112d7fdc49c917c69661b437321df',  # REV12 baseline
    '9b80d7a5aa0aa7b0e5e2dd8b1e54958f88f607c7',  # REV31 exact reviewed blob
}, 'Unreviewed AdbProtocol change'
assert 'fun sideloadZip(file: File): SideloadResult' in adb
assert 'source.open(resolver)' in adb
assert 'return opened.use { reader ->' in adb
assert 'reader.readFullyAt(offset, payload)' in adb
assert 'RandomAccessFile(file, "r").use { raf ->' not in adb.split('fun sideloadZip(')[1].split('ADB SYNC / FILE TRANSFER')[0]

assert 'data class SafDocument(' in source
assert 'provider.openFileDescriptor(uri, "r")' in source
assert 'channel.position(0L)' in source
assert 'channel.read(bytes, offset + read.toLong())' in source
assert 'stream.close()' in source and 'descriptor.close()' in source
assert 'java.nio.ByteBuffer' in source

assert 'registerDirectSideloadLauncher()' in main
assert 'startDirectSideloadFilePicker()' in main
assert 'viewModel.runSideload(source)' in main
assert 'R.id.btnSideloadImport).setOnClickListener { startImportFilePicker() }' in main
assert 'private val folderName = "NekoFlash"' in main
assert 'fun runSideload(file: File) = runSideload(FirmwareSource.WorkspaceFile(file))' in vm
assert 'proto.sideloadZip(source, getApplication<Application>().contentResolver)' in vm
assert 'persistPendingSideloadVerification(source)' in vm
assert (root/'app/src/test/java/ru/forum/adbfastboottool/FirmwareSourceTest.kt').is_file()
print('PASS REV12: seekable SAF direct Sideload, fallback import, verified recovery, paths unchanged')
