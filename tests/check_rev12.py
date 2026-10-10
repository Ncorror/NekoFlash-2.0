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

# Narrow audited protocol change: no modifications to Fastboot or native USBFS.
data = adb_file.read_bytes()
git_blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert git_blob == '0487a8b60a27c2bfe8809f4db5db066ffb352777', 'Unreviewed AdbProtocol change'
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
assert 'persistPendingSideloadVerification(source, proto)' in vm
assert (root/'app/src/test/java/ru/forum/adbfastboottool/FirmwareSourceTest.kt').is_file()
print('PASS REV12: seekable SAF direct Sideload, fallback import, verified recovery, paths unchanged')
