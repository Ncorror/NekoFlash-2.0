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
# REV52 audits the exact diagnostic-only ADB revision. Convert only those
# three reviewed log enhancements back to the REV51 binary source and
# assert full original hash before legacy protocol-protection checks.
# Do NOT relax or disable the transport integrity guard.
def source_git_sha(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
rev52_adb_hash = '5a4f9c9640e13b404ab1706a0b07dcfc40ee30ea'
# REV54: audit ONLY the reviewed host-to-adbd USB ZLP framing change.
# Before any older checksum/invariant checks, reverse the exact diff into
# the byte-for-byte accepted REV53 ADB source. Never waive USB review.
rev54_adb_hash = '2f1d8fd57f2ac28f99e4bd79fb6885ec41dad2ed'
if source_git_sha(data) == rev54_adb_hash:
    rev54_reviewed_hunks = [
        ("        if (!bulkWriteFully(header)) throw Exception(\"ADB header transfer error\")\n        if (!finishAdbUsbOutTransfer(header.size)) {\n            throw Exception(\"ADB header USB ZLP transfer error (bytes=${header.size})\")\n        }\n", "        if (!bulkWriteFully(header)) throw Exception(\"ADB header transfer error\")\n"),
        ("            }\n            if (!finishAdbUsbOutTransfer(data.size)) {\n                throw Exception(\"ADB payload USB ZLP transfer error (bytes=${data.size})\")\n            }\n        }", "            }\n        }"),
        ("\n    /**\n     * AOSP adb requires a USB zero-length packet at the end of any host OUT\n     * header/payload whose byte length is an exact multiple of wMaxPacketSize.\n     * Call under adbWriteLock, *after* writing all data bytes and *before*\n     * waiting for the stream's A_OKAY. An empty transfer is not an ADB byte.\n     * A ZLP failure is a real transport error, never silently retried.\n     */\n    private fun finishAdbUsbOutTransfer(transferBytes: Int): Boolean {\n        val conn = connection ?: return false\n        val ep = endpointOut ?: return false\n        return AdbUsbBulkOutPolicy.finishTransfer(transferBytes, ep.maxPacketSize) {\n            conn.bulkTransfer(ep, EMPTY_PAYLOAD, 0, 0, 5_000)\n        }\n    }\n\n    private fun bulkWriteFully(data: ByteArray, offset: Int = 0, length: Int = data.size, timeout: Int = 5000): Boolean {", "\n    private fun bulkWriteFully(data: ByteArray, offset: Int = 0, length: Int = data.size, timeout: Int = 5000): Boolean {"),
    ]
    for revised, previous in rev54_reviewed_hunks:
        assert data.count(revised.encode('utf-8')) == 1, 'Unreviewed REV54 USB change'
        data = data.replace(revised.encode('utf-8'), previous.encode('utf-8'), 1)
    assert source_git_sha(data) == '9479737a5cc25bbfc08a0880148f2ce8fddee09c', 'REV54 modified unrelated ADB code'
    policy = (base/'AdbUsbBulkOutPolicy.kt').read_text()
    assert 'transferBytes % maxPacketSize == 0' in policy
    assert 'sendZlp() == 0' in policy
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/AdbUsbBulkOutPolicyTest.kt').is_file()
# REV53: normalize only the seven audited changes to the one-shot
# shell-v2 CLOSE_STDIN race, then run every existing strict ADB guard.
# Do not permit arbitrary transport edits or silently skip source integrity.
rev53_adb_hash = '9479737a5cc25bbfc08a0880148f2ce8fddee09c'
if source_git_sha(data) == rev53_adb_hash:
    reviewed_hunks = [
        ("            // Close stdin for one-shot shell/v2 commands.\n            val stdinCloseAcked = writeShellPacket(\n                stream, SHELL_ID_CLOSE_STDIN, EMPTY_PAYLOAD, allowBufferedEarlyClose = true\n            )\n            if (!ShellV2EarlyClosePolicy.mayDrainAfterCloseStdin(\n                    stdinCloseAcked, stream.closed, pendingByteCount(stream), cancelled\n                )) {\n                return result(false)\n            }\n            if (!stdinCloseAcked && logOutput) {\n                onLog(\"ℹ️ shell_v2: peer closed during CLOSE_STDIN; decoding already buffered response (no retry)\")\n            }\n\n            // The peer can close after sending the complete reply but before\n            // ACKing CLOSE_STDIN. Drain *only* bytes received for this local\n            // stream. A truncated/malformed EXIT never counts as success.\n            while (!cancelled && (!stream.closed || pendingByteCount(stream) > 0)) {\n                val header = readShellPacketHeader(stream)\n                if (header == null) {\n                    if (stream.closed && pendingByteCount(stream) > 0) {\n                        onLog(\"❌ shell_v2: incomplete buffered frame after peer close\")\n                        return result(false)\n                    }\n                    break\n                }\n                when (header.id) {", "            // Close stdin for one-shot shell/v2 commands.\n            if (!writeShellPacket(stream, SHELL_ID_CLOSE_STDIN, EMPTY_PAYLOAD)) {\n                return result(false)\n            }\n\n            while (!cancelled && !stream.closed) {\n                val header = readShellPacketHeader(stream) ?: break\n                when (header.id) {"),
        ("                    SHELL_ID_EXIT -> {\n                        val data = readAdbStreamExact(stream, header.length) ?: return result(false)\n                        exitCode = ShellV2EarlyClosePolicy.completeExitCode(header.length, data)\n                            ?: run {\n                                onLog(\"❌ shell_v2: invalid EXIT payload length=${header.length}\")\n                                return result(false)\n                            }\n                        // Exit success requires the real explicit one-byte code.\n                    }", "                    SHELL_ID_EXIT -> {\n                        val data = readAdbStreamExact(stream, header.length) ?: EMPTY_PAYLOAD\n                        exitCode = data.firstOrNull()?.toInt()?.and(0xFF) ?: 0\n                        // The final decoder flush logs any trailing line before EXIT.\n                    }"),
        ("            if (exitCode == null && logOutput) onLog(\"⚠️ shell_v2 finished without an exit packet\")\n            return result(ShellV2EarlyClosePolicy.succeeded(exitCode, cancelled))\n        } catch (error: Exception) {", "            if (exitCode == null && logOutput) onLog(\"⚠️ shell_v2 finished without an exit packet\")\n            return result(exitCode == 0 && !cancelled)\n        } catch (error: Exception) {"),
        ("     */\n    private fun writeAdbStream(\n        stream: AdbStream, payload: ByteArray, allowBufferedEarlyClose: Boolean = false\n    ): Boolean {\n        if (stream.closed || cancelled) return false\n        if (payload.size <= peerMaxPayloadSize) {\n            return writeAdbStreamChunk(stream, payload, allowBufferedEarlyClose)\n        }\n        for (slice in AdbInteractiveInputChunkPolicy.fragments(\n            payload.size, peerMaxPayloadSize, framingBytes = 0\n        )) {\n            if (stream.closed || cancelled) return false\n            if (!writeAdbStreamChunk(\n                    stream, payload.copyOfRange(slice.first, slice.last + 1), allowBufferedEarlyClose\n                )) {\n                return false", "     */\n    private fun writeAdbStream(stream: AdbStream, payload: ByteArray): Boolean {\n        if (stream.closed || cancelled) return false\n        if (payload.size <= peerMaxPayloadSize) {\n            return writeAdbStreamChunk(stream, payload)\n        }\n        for (slice in AdbInteractiveInputChunkPolicy.fragments(\n            payload.size, peerMaxPayloadSize, framingBytes = 0\n        )) {\n            if (stream.closed || cancelled) return false\n            if (!writeAdbStreamChunk(stream, payload.copyOfRange(slice.first, slice.last + 1))) {\n                return false"),
        ("\n    private fun writeAdbStreamChunk(\n        stream: AdbStream, payload: ByteArray, allowBufferedEarlyClose: Boolean\n    ): Boolean {\n        if (stream.closed || cancelled) return false", "\n    private fun writeAdbStreamChunk(stream: AdbStream, payload: ByteArray): Boolean {\n        if (stream.closed || cancelled) return false"),
        ("                    closeAdbStream(stream)\n                    // Only a one-shot shell/v2 CLOSE_STDIN caller may recover\n                    // by decoding its same-channel buffered response. All\n                    // other writes still fail; no command is ever retried.\n                    val buffered = pendingByteCount(stream)\n                    val canInspect = allowBufferedEarlyClose && buffered > 0\n                    val symbol = if (canInspect) \"ℹ️\" else \"❌\"\n                    onLog(\"$symbol ADB stream closed during write local=${stream.localId}, \" +\n                        \"expectedRemote=$expectedRemote, receivedRemote=${header.arg0}, \" +\n                        \"payloadBytes=${payload.size}, bufferedResponseBytes=$buffered, \" +\n                        \"dispatcherGeneration=${dispatcherGeneration.get()}, transportConnected=$isConnected\")", "                    closeAdbStream(stream)\n                    // This may be a legitimately early remote close, or a\n                    // misrouted/stale packet. Never infer command execution or\n                    // automatically replay an arbitrary shell command.\n                    onLog(\"❌ ADB stream closed during write local=${stream.localId}, \" +\n                        \"expectedRemote=$expectedRemote, receivedRemote=${header.arg0}, \" +\n                        \"payloadBytes=${payload.size}, bufferedResponseBytes=${pendingByteCount(stream)}, \" +\n                        \"dispatcherGeneration=${dispatcherGeneration.get()}, transportConnected=$isConnected\")"),
        ("\n    private fun writeShellPacket(\n        stream: AdbStream, id: Int, payload: ByteArray,\n        allowBufferedEarlyClose: Boolean = false\n    ): Boolean {\n        val packet = ByteBuffer.allocate(SHELL_PACKET_HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {\n            put(id.toByte())\n            putInt(payload.size)\n            put(payload)\n        }.array()\n        return writeAdbStream(stream, packet, allowBufferedEarlyClose)\n    }", "\n    private fun writeShellPacket(stream: AdbStream, id: Int, payload: ByteArray): Boolean {\n        val packet = ByteBuffer.allocate(SHELL_PACKET_HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {\n            put(id.toByte())\n            putInt(payload.size)\n            put(payload)\n        }.array()\n        return writeAdbStream(stream, packet)\n    }"),
    ]
    for revised, previous in reviewed_hunks:
        assert data.count(revised.encode('utf-8')) == 1, 'Unreviewed REV53 ADB change'
        data = data.replace(revised.encode('utf-8'), previous.encode('utf-8'), 1)
    assert source_git_sha(data) == rev52_adb_hash, 'REV53 modified unrelated ADB transport'
if source_git_sha(data) == rev52_adb_hash:
    original_stale = 'onLog("ℹ️ ADB open: ignored stale CLSE for local=${header.arg1}, expected local=$localId")'
    rev52_stale = 'onLog("ℹ️ ADB open: ignored stale CLSE local=${header.arg1}, remote=${header.arg0}, expected local=$localId, generation=${dispatcherGeneration.get()}")'
    original_timeout = 'onLog("❌ ADB stream: no ACK for WRTE")'
    rev52_timeout = 'onLog("❌ ADB stream: no ACK for WRTE local=${stream.localId}, remote=${stream.remoteId}, bytes=${payload.size}, pending=${pendingByteCount(stream)}, generation=${dispatcherGeneration.get()}")'
    original_close = (
        '                    stream.remoteId = header.arg0\n'
        '                    closeAdbStream(stream)\n'
        '                    onLog("❌ ADB stream closed during write")'
    )
    rev52_close = (
        '                    val expectedRemote = stream.remoteId\n'
        '                    stream.remoteId = header.arg0\n'
        '                    closeAdbStream(stream)\n'
        '                    // This may be a legitimately early remote close, or a\n'
        '                    // misrouted/stale packet. Never infer command execution or\n'
        '                    // automatically replay an arbitrary shell command.\n'
        '                    onLog("❌ ADB stream closed during write local=${stream.localId}, " +\n'
        '                        "expectedRemote=$expectedRemote, receivedRemote=${header.arg0}, " +\n'
        '                        "payloadBytes=${payload.size}, bufferedResponseBytes=${pendingByteCount(stream)}, " +\n'
        '                        "dispatcherGeneration=${dispatcherGeneration.get()}, transportConnected=$isConnected")'
    )
    for reviewed, original in ((rev52_stale, original_stale),
                               (rev52_timeout, original_timeout),
                               (rev52_close, original_close)):
        assert data.count(reviewed.encode('utf-8')) == 1, 'Unreviewed REV52 ADB change'
        data = data.replace(reviewed.encode('utf-8'), original.encode('utf-8'), 1)
    assert source_git_sha(data) == '0362bbb3acb6516684f9dfca8dea8abd98b4a430', 'REV52 changed USB protocol code'
# REV45: lint mistook a path on the *remote ADB target* for one on the
# Android host. Normalize only this narrowly reviewed helper extraction back
# to REV43 before applying the original whole-file integrity checks.
rev45_helper = (
    b'    // This is a *target-device* ADB shell path, not an Android host path.\n'
    b'    // Environment.getExternalStorageDirectory() would refer to the wrong phone.\n'
    b'    @SuppressLint("SdCardPath")\n'
    b'    private fun remoteObbDirectory(packageName: String): String =\n'
    b'        "/sdcard/Android/obb/$packageName"\n\n'
)
if b'remoteObbDirectory(packageName)' in data:
    assert data.count(rev45_helper) == 1, 'Unreviewed target OBB path helper'
    assert data.count(b'val remoteDir = remoteObbDirectory(packageName)') == 1
    assert data.count(b'import android.annotation.SuppressLint\n') == 1
    data = data.replace(b'import android.annotation.SuppressLint\n', b'', 1)
    data = data.replace(rev45_helper, b'', 1)
    data = data.replace(b'val remoteDir = remoteObbDirectory(packageName)',
                        b'val remoteDir = "/sdcard/Android/obb/$packageName"', 1)
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
    # The exact REV53 source has already been reverse-audited into the
    # previous ADB blob in data; verify the original chunking invariants
    # there, while still enforcing the full reviewed REV53 source hash.
    stream_writer = data.decode('utf-8').split('private fun writeAdbStream(stream: AdbStream, payload: ByteArray): Boolean')[1].split('private fun readAdbStreamExact(')[0]
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
picker_body = main.split('private fun registerDirectSideloadLauncher()')[1].split('private fun startDirectSideloadFilePicker()')[0]
assert 'pendingSideloadSource = source' in picker_body
assert 'viewModel.runSideload(source)' not in picker_body
assert 'R.id.btnStartSideload).setOnClickListener' in main
assert 'viewModel.adbPeerMode.value != AdbProtocol.PeerMode.SIDELOAD' in main
assert 'viewModel.runSideload(source)' in main
assert 'R.id.btnSideloadImport).setOnClickListener { startImportFilePicker() }' in main
assert 'android:id="@+id/btnStartSideload"' in (root/'app/src/main/res/layout/page_adb.xml').read_text()
assert 'private val folderName = "NekoFlash"' in main
assert 'fun runSideload(file: File) = runSideload(FirmwareSource.WorkspaceFile(file))' in vm
assert 'proto.sideloadZip(source, getApplication<Application>().contentResolver)' in vm
assert 'persistPendingSideloadVerification(source)' in vm
assert (root/'app/src/test/java/ru/forum/adbfastboottool/FirmwareSourceTest.kt').is_file()
print('PASS REV12: seekable SAF direct Sideload, fallback import, verified recovery, paths unchanged')
