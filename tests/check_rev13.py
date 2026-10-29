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

assert git_blob(fastboot_file) == '2b824e0ae6df1ef6dea7f608d5c048dec17a70bf'
assert git_blob(native_file) == 'fa4d1fc97e39c7ef598037281bae39fba0c48d97'
assert git_blob(backend_file) == '9cb1de4aebdf9a680f2a468c739d2324e4f59dfd'

# REV21 exposes the existing download limit to the host plan without changing DATA transfer.
# REV19 re-audits serial binding and the optional native total deadline.
# REV18 fetch integrity and literal targets remain covered.
# File APIs remain valid for terminal, Mass Flash and Mi Unlock.
assert 'fun flashPartitionDetailed(partition: String, file: File): FlashResult' in fastboot
assert 'flashPartitionDetailedInternal(partition, file, file.name, file.length(), null)' in fastboot
assert 'transferDownloadPayload(file, "flash:$normalizedPartition")' in fastboot
assert 'fun runFlash(partition: String, file: File, slot: String? = null, expectedSerial: String? = null)' in vm
assert 'fun runFastbootDownloadAndRun(file: File, commandAfterDownload: String, expectedSerial: String? = null)' in vm

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
assert 'return FirmwareSource.SafDocument(uri, name, queryFileSize(uri))' in main
assert 'viewModel.selectQuickFlashSource(FirmwareSource.WorkspaceFile(it))' in main
assert 'viewModel.runFlash(partition, selection.source(), chosenSlot, expectedSizeBytes = selection.expectedSizeBytes)' in main
assert 'viewModel.runFlash(partition, file, slot)' in main  # legacy quick actions
assert 'viewModel.addFlashQueueFile(partition, file)' in main
assert 'private val folderName = "NekoFlash"' in main
print('PASS REV13: direct SAF via Native USBFS dup(fd), original File and URB safety, RU/EN')
