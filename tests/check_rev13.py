#!/usr/bin/env python3
"""REV13: audited Fastboot SAF fd path, preserves original native transfer safety."""
from pathlib import Path
import hashlib
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev12.py')], cwd=root, check=True)
base = root/'app/src/main'
jvm = base/'java/ru/forum/adbfastboottool'
fastboot_file = jvm/'FastbootProtocol.kt'
native_file = base/'cpp/native_usbfs.cpp'
backend_file = jvm/'NativeUsbfsBackend.kt'
main = (jvm/'MainActivity.kt').read_text()
vm = (jvm/'DeviceViewModel.kt').read_text()
fastboot = fastboot_file.read_text()
native = native_file.read_text()
backend = backend_file.read_text()
firmware = (jvm/'FirmwareSource.kt').read_text()

def git_blob(path):
    data = path.read_bytes()
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

assert git_blob(fastboot_file) == '26482dcb1a8876ddfa42455981a9a5ebaf32791d'
assert git_blob(native_file) == '886b274e20e6e356bc806250314914a9c2c24de0'
assert git_blob(backend_file) == '5bb8a48827800dd8ea1fc601194b3566b7da91c6'

# File APIs remain valid for terminal, Mass Flash and Mi Unlock.
assert 'fun flashPartitionDetailed(partition: String, file: File): FlashResult' in fastboot
assert 'flashPartitionDetailedInternal(partition, file, file.name, file.length(), null)' in fastboot
assert 'transferDownloadPayload(file, "flash:$normalizedPartition")' in fastboot
assert 'fun runFlash(partition: String, file: File, slot: String? = null)' in vm
assert 'fun runFastbootDownloadAndRun(file: File, commandAfterDownload: String)' in vm

# Direct SAF requires native support and a read-only seekable descriptor, not a
# silent fallback to a different USB DATA transport or a workspace copy.
assert 'source.open(resolver)' in fastboot
assert 'return reader.use { opened ->' in fastboot
assert 'dataTransportMode != DataTransportMode.NATIVE_USBFS' in fastboot
assert 'transferDownloadPayloadNativeUsbfs(' in fastboot
assert 'sourceFd ?: -1' in fastboot
assert 'override val nativeFd: Int = descriptor.fd' in firmware
assert 'provider.openFileDescriptor(uri, "r")' in firmware

assert 'payloadFd: Int = -1' in backend
assert 'payloadFile?.absolutePath' in backend
assert 'payloadSizeBytes: Long = -1L' in backend
assert 'nativeBulkOutUrb(' in backend
assert 'jint supplied_payload_fd' in native
assert 'const int duplicated_fd = dup(supplied_payload_fd)' in native
assert 'payload_fd.reset(duplicated_fd)' in native
assert 'fstat(payload_fd.get(), &payload_stat)' in native
assert 'pread_exact(' in native
assert 'PendingUrbOwnershipGuard pending_urb_guard(slots)' in native
assert 'USBDEVFS_DISCARDURB' in native and 'USBDEVFS_REAPURBNDELAY' in native
assert 'poison_backend_noexcept()' in native

# New primary Quick-Flash picker, existing workspace choice and GUI controls.
assert 'registerQuickImageLauncher()' in main
assert 'startQuickImageDocumentPicker()' in main
assert 'quickFlashSource = FirmwareSource.SafDocument(' in main
assert 'quickFlashSource = FirmwareSource.WorkspaceFile(file)' in main
assert 'viewModel.runFlash(partition, source, chosenSlot)' in main
assert 'viewModel.runFlash(partition, file, slot)' in main  # legacy quick actions
assert 'viewModel.addFlashQueueFile(partition, file)' in main
assert 'private val folderName = "NekoFlash"' in main
print('PASS REV13: direct SAF via Native USBFS dup(fd), original File and URB safety, RU/EN')
