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
# Allow only exact audited ADB source variants. REV31 added lossless disk
# spooling, REV33 file-backed directory parsing, REV35 UTF-8 display rendering.
# The original SAF/sideload transport invariants remain guarded below.
assert git_blob in {
    '7acf241d15e112d7fdc49c917c69661b437321df',  # REV12 baseline
    '9b80d7a5aa0aa7b0e5e2dd8b1e54958f88f607c7',  # REV31 exact reviewed blob
    'd6c43f7bfe4732bce5937ebf2f234bdb1e65cc8e',  # REV33 raw-file directory listing
    '6f975b156a643323b2ebc42916086e2d4b2e91e1',  # REV35 packet-safe UTF-8 terminal rendering
    '3057cc39fe64c5b4e2cc67a039f71d06c9f4618e',  # REV36 interactive PTY packet-safe output
    'cab103cc17fca1840525380d8f9065533bac96de',  # REV37 chunked PTY stdin from CNXN peer maxdata
    'd027c6a2f196269f139f1a416c3cbc9bceef3ec8',  # REV42 generic ADB stream A_WRTE peer maxdata
    '794da944cb1a9f35106fd08f5f1f0612e53fe5aa',  # REV43 lazy recursive push and cancellation
    'da6eb25a9b5de629211f69cee7ae849c32c13d72',  # REV43 preserve pushPath cancellation across calls
}, 'Unreviewed AdbProtocol change'
if git_blob in {
    'd6c43f7bfe4732bce5937ebf2f234bdb1e65cc8e',
    '6f975b156a643323b2ebc42916086e2d4b2e91e1',
    '3057cc39fe64c5b4e2cc67a039f71d06c9f4618e',
    'cab103cc17fca1840525380d8f9065533bac96de',
    'd027c6a2f196269f139f1a416c3cbc9bceef3ec8',
    '794da944cb1a9f35106fd08f5f1f0612e53fe5aa',  # REV43 lazy recursive push and cancellation
    'da6eb25a9b5de629211f69cee7ae849c32c13d72',  # REV43 preserve pushPath cancellation across calls
}:
    # Do not allow the new variant to silently fall back to full-result heap
    # capture, lose raw evidence or bypass path validation.
    assert 'runShellCommandForResult(listCommand, logOutput = false, captureOutput = false)' in adb
    assert 'stdoutFile.reader(Charsets.UTF_8).use { reader ->' in adb
    assert 'AdbPullPathPolicy.parseListing(reader)' in adb
    pull = (base/'AdbPullPathPolicy.kt').read_text()
    assert 'fun parseListing(reader: Reader)' in pull
    assert 'require(normalize(record).isNotEmpty())' in pull
if git_blob in {'6f975b156a643323b2ebc42916086e2d4b2e91e1', '3057cc39fe64c5b4e2cc67a039f71d06c9f4618e', 'cab103cc17fca1840525380d8f9065533bac96de', 'd027c6a2f196269f139f1a416c3cbc9bceef3ec8', '794da944cb1a9f35106fd08f5f1f0612e53fe5aa', 'da6eb25a9b5de629211f69cee7ae849c32c13d72'}:
    # Only UI text rendering changed: raw binary writes and protocol identity
    # must stay untouched; stdout and stderr keep independent decoder state.
    assert 'record.writeStdout(data)' in adb and 'record.writeStderr(data)' in adb
    assert 'ShellOutputTextRenderer(false, onLog)' in adb
    assert 'ShellOutputTextRenderer(true, onLog)' in adb
    assert 'stdoutDisplay?.accept(data)' in adb
    assert 'stderrDisplay?.accept(data)' in adb
    assert 'stdoutDisplay?.finish()' in adb
    assert 'stderrDisplay?.finish()' in adb
    assert 'if (logOutput && exitCode != null) onLog("=== ADB SHELL EXIT: $exitCode ===")' in adb
    renderer = (base/'ShellOutputTextRenderer.kt').read_text()
    assert 'StandardCharsets.UTF_8.newDecoder()' in renderer
    assert 'MAX_VIEW_FRAGMENT = 8192' in renderer
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/ShellOutputTextRendererTest.kt').is_file()
if git_blob in {'3057cc39fe64c5b4e2cc67a039f71d06c9f4618e', 'cab103cc17fca1840525380d8f9065533bac96de', 'd027c6a2f196269f139f1a416c3cbc9bceef3ec8', '794da944cb1a9f35106fd08f5f1f0612e53fe5aa', 'da6eb25a9b5de629211f69cee7ae849c32c13d72'}:
    interactive = adb.split('private fun runInteractiveShellV2(')[1].split('data class CapturedShellResult(')[0]
    assert 'ShellOutputTextRenderer(false, onLog)' in interactive
    assert 'ShellOutputTextRenderer(true, onLog)' in interactive
    assert 'SHELL_ID_STDOUT -> stdoutDisplay.accept(payload)' in interactive
    assert 'SHELL_ID_STDERR -> stderrDisplay.accept(payload)' in interactive
    assert 'display.accept(data)' in interactive
    assert 'flushVisibleFragment()' in interactive
    assert 'stdoutDisplay.finish()' in interactive and 'stderrDisplay.finish()' in interactive
    assert 'display.finish()' in interactive
    assert 'fun flushVisibleFragment()' in renderer
if git_blob in {'cab103cc17fca1840525380d8f9065533bac96de', 'd027c6a2f196269f139f1a416c3cbc9bceef3ec8', '794da944cb1a9f35106fd08f5f1f0612e53fe5aa', 'da6eb25a9b5de629211f69cee7ae849c32c13d72'}:
    assert 'peerMaxPayloadSize = AdbInteractiveInputChunkPolicy.maxWirePayload(header.arg1, MAX_PAYLOAD)' in adb
    assert 'writeInteractiveStdin(stream, payload, shellV2 = true)' in adb
    assert 'writeInteractiveStdin(stream, payload, shellV2 = false)' in adb
    assert 'for (slice in AdbInteractiveInputChunkPolicy.fragments(' in adb
    assert 'payload.copyOfRange(slice.first, slice.last + 1)' in adb
    assert 'if (cancelled || stream.closed) return false' in adb
    assert 'if (!sent) return false' in adb
    assert (base/'AdbInteractiveInputChunkPolicy.kt').is_file()
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/AdbInteractiveInputChunkPolicyTest.kt').is_file()
if git_blob in {'d027c6a2f196269f139f1a416c3cbc9bceef3ec8', '794da944cb1a9f35106fd08f5f1f0612e53fe5aa', 'da6eb25a9b5de629211f69cee7ae849c32c13d72'}:
    # Bytes inside one logical SYNC request must not be trimmed or re-encoded.
    # A_WRTE is bounded by peer CNXN maxdata, ACKed sequentially.
    stream_writer = adb.split('private fun writeAdbStream(stream: AdbStream, payload: ByteArray): Boolean')[1].split('private fun readAdbStreamExact(')[0]
    assert 'payload.size <= peerMaxPayloadSize' in stream_writer
    assert 'AdbInteractiveInputChunkPolicy.fragments(' in stream_writer
    assert 'framingBytes = 0' in stream_writer
    assert 'writeAdbStreamChunk(stream, payload.copyOfRange(slice.first, slice.last + 1))' in stream_writer
    assert 'if (stream.closed || cancelled) return false' in stream_writer
    assert 'sendMessageInternal(A_WRTE, stream.localId, stream.remoteId, payload)' in stream_writer
    assert 'stream.pending.add(data)' in stream_writer
    assert 'if (!packetTargetsLocalStream(header, stream.localId)) continue' in stream_writer
    assert 'writeSyncData(stream, "DATA", buffer, read)' in adb
    assert 'private val SYNC_DATA_CHUNK = 64 * 1024' in adb
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/AdbInteractiveInputChunkPolicyTest.kt').is_file()
if git_blob in {'794da944cb1a9f35106fd08f5f1f0612e53fe5aa', 'da6eb25a9b5de629211f69cee7ae849c32c13d72'}:
    # REV43 differs from REV42 only in the audited recursive directory path
    # and the split public/new-operation vs nested/no-reset file writer.
    push_dir = adb.split('private fun pushDirectory(')[1].split('fun pullFile(')[0]
    assert 'AdbPushDirectoryWalker(localDir)' in push_dir
    assert 'walker.scan(checkCancelled)' in push_dir
    assert 'walker.visitDirectories(checkCancelled)' in push_dir
    assert 'walker.visitFiles(checkCancelled)' in push_dir
    assert 'afterDirs != expected' in push_dir and 'afterFiles != expected' in push_dir
    assert 'pushFileInternal(file, remoteFile, mode, startNewOperation = false)' in push_dir
    assert 'walkTopDown().toList()' not in push_dir
    assert 'if (startNewOperation) cancelled = false' in adb
    assert 'if (cancelled || !isConnected) return false' in adb
    walker = (base/'AdbPushDirectoryWalker.kt').read_text()
    assert 'root.walkTopDown()' in walker and '.onEnter' in walker and '.onFail' in walker
    assert 'Files.isSymbolicLink(' in walker
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/AdbPushDirectoryWalkerTest.kt').is_file()
if git_blob == 'da6eb25a9b5de629211f69cee7ae849c32c13d72':
    # One reset only at standalone-operation entry, never between nested
    # pushPath/pushDirectory/pushFileInternal operations.
    push_path = adb.split('fun pushPath(')[1].split('fun pushFile(')[0]
    push_dir = adb.split('private fun pushDirectory(')[1].split('fun pullFile(')[0]
    assert 'pushFileInternal(localPath, remotePath, mode, startNewOperation = false)' in push_path
    assert 'cancelled = false' not in push_dir
    assert 'if (cancelled || !isConnected) return false' in push_dir
    assert 'if (!pushFileInternal(file, remoteFile, mode, startNewOperation = false))' in push_dir
    assert 'if (!pushFileInternal(file, remoteFile, mode, startNewOperation = false)) {\n                    checkCancelled()' in push_dir
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
