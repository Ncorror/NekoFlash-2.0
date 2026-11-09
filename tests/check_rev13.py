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

# Preserve REV13's byte-exact Fastboot transfer safety: REV29 permits ONE
# exact, narrowly scoped terminal empty-read-wait change only. Restore the
# original terminal block in memory and compare the full blob to REV13 SHA.
# Any other FastbootProtocol change (especially DATA/USB paths) still fails.
# REV41 changes only the post-DATA final-response wait in downloadAndRun.
# Undo that exact reviewed block *before* the original REV13+REV29 blob
# verification. Every other FastbootProtocol byte is still guarded.
rev41_original = "        val downloadDone = readUntilFinal(30000) ?: return false"
rev41_replacement = "        // The device can send INFO fragments before the final OKAY/FAIL.\n        // Keep the same 30-second overall budget as the flash path, but\n        // poll in cancellable slices rather than one long USB read.\n        val downloadDone = readUntilFinalWithRetry(\n            singleReadTimeoutMs = FastbootPostDownloadWaitPolicy.READ_SLICE_MS,\n            maxTotalTimeMs = FastbootPostDownloadWaitPolicy.FINAL_TIMEOUT_MS\n        ) ?: return false"
if rev41_replacement in fastboot:
    section = fastboot.split('fun downloadAndRun(file: File, commandAfterDownload: String)')[1].split('fun stageAndOemUnlock(')[0]
    assert section.count(rev41_replacement) == 1
    assert (jvm/'FastbootPostDownloadWaitPolicy.kt').is_file()
    assert (root/'app/src/test/java/ru/forum/adbfastboottool/FastbootPostDownloadWaitPolicyTest.kt').is_file()
    protected_fastboot = fastboot.replace(rev41_replacement, rev41_original, 1)
else:
    protected_fastboot = fastboot
if git_blob(fastboot_file) != '2b824e0ae6df1ef6dea7f608d5c048dec17a70bf':
    rev29_block = "        // Driver read failures can return immediately. Use monotonic time\n        // for the 120-second deadline and a short cancellable backoff on empty\n        // responses; do not resend commands or alter raw response logging.\n        val startedNs = System.nanoTime()\n        var infoLines = 0\n        var lastWaitLogSec = 0L\n\n        while (!cancelled) {\n            val elapsedMs = FastbootTerminalWaitPolicy.elapsedMs(startedNs, System.nanoTime())\n            if (elapsedMs >= FastbootTerminalWaitPolicy.COMMAND_DEADLINE_MS) {\n                val message = \"Terminal Fastboot command timed out: $clean\"\n                onLog(\"❌ $message\")\n                markSessionBroken(message)\n                return@withLock false\n            }\n\n            val readStartedNs = System.nanoTime()\n            val packet = readPacket(2000)\n            if (packet == null) {\n                val readFinishedNs = System.nanoTime()\n                val afterReadMs = FastbootTerminalWaitPolicy.elapsedMs(startedNs, readFinishedNs)\n                val elapsedSec = afterReadMs / 1000\n                if (elapsedSec / 10 != lastWaitLogSec / 10) {\n                    onLog(\"⏳ Fastboot is waiting for a response... ${elapsedSec} sec\")\n                    lastWaitLogSec = elapsedSec\n                }\n                val remainingMs =\n                    (FastbootTerminalWaitPolicy.COMMAND_DEADLINE_MS - afterReadMs).coerceAtLeast(0L)\n                val backoffMs = FastbootTerminalWaitPolicy.retryDelayMs(\n                    readFinishedNs - readStartedNs, remainingMs\n                )\n                if (backoffMs > 0L && !cancelled) {\n                    try {\n                        Thread.sleep(backoffMs)\n                    } catch (_: InterruptedException) {\n                        Thread.currentThread().interrupt()\n                        onLog(\"⚠️ Fastboot terminal wait interrupted\")\n                        return@withLock false\n                    }\n                }\n                continue\n            }\n\n"
    rev13_block = "        val startedMs = System.currentTimeMillis()\n        var infoLines = 0\n        var lastWaitLogSec = 0L\n\n        while (!cancelled) {\n            val elapsedMs = System.currentTimeMillis() - startedMs\n            if (elapsedMs >= 120_000L) {\n                val message = \"Terminal Fastboot command timed out: $clean\"\n                onLog(\"❌ $message\")\n                markSessionBroken(message)\n                return@withLock false\n            }\n\n            val packet = readPacket(2000)\n            if (packet == null) {\n                val elapsedSec = elapsedMs / 1000\n                if (elapsedSec / 10 != lastWaitLogSec / 10) {\n                    onLog(\"⏳ Fastboot is waiting for a response... ${elapsedSec} sec\")\n                    lastWaitLogSec = elapsedSec\n                }\n                continue\n            }\n\n"
    assert protected_fastboot.count(rev29_block) == 1, "Unexpected Fastboot terminal patch"
    protected_source = protected_fastboot.replace(rev29_block, rev13_block, 1).encode("utf-8")
    protected_hash = hashlib.sha1(
        b"blob " + str(len(protected_source)).encode() + b"\0" + protected_source
    ).hexdigest()
    assert protected_hash == '2b824e0ae6df1ef6dea7f608d5c048dec17a70bf', (
        "FastbootProtocol changed outside the audited REV29 terminal wait block"
    )
native_data = native_file.read_bytes()
native_hash = hashlib.sha1(
    b'blob ' + str(len(native_data)).encode() + b'\0' + native_data
).hexdigest()
if native_hash == '86f81542e6d66290fc5938eb32f8c39c1e5028dd':
    adb_in_native = "static jlongArray native_adb_bulk_in_exact_impl(\n    JNIEnv* env,\n    jint fd,\n    jint endpoint_address,\n    jbyteArray destination,\n    jint offset,\n    jint length,\n    jint timeout_ms) {\n\n    const int64_t started = now_ms();\n    if (fd < 0 || destination == nullptr || offset < 0 || length <= 0 || timeout_ms <= 0 ||\n        (endpoint_address & 0x80) == 0 || (endpoint_address & 0x0F) == 0) {\n        return make_result(env, 0, 0, EINVAL, 0, 0, 0, STAGE_PREFLIGHT);\n    }\n\n    const jsize destination_size = env->GetArrayLength(destination);\n    if (offset > destination_size || length > destination_size - offset) {\n        return make_result(env, 0, 0, EINVAL, 0, 0, 0, STAGE_PREFLIGHT);\n    }\n\n    UniqueFd usb_fd(dup(fd));\n    if (!usb_fd.valid()) {\n        const int dup_errno = errno;\n        return make_result(env, 0, 0, dup_errno, 0, 0,\n                           now_ms() - started, STAGE_OPEN, 0, 0, 0,\n                           DRAIN_NOT_NEEDED, 0, 0, dup_errno);\n    }\n\n    std::vector<UrbSlot> slots;\n    try {\n        slots.emplace_back(static_cast<size_t>(length));\n    } catch (const std::bad_alloc&) {\n        return make_result(env, 0, 0, ENOMEM, 0, 0,\n                           now_ms() - started, STAGE_PREFLIGHT);\n    } catch (...) {\n        return make_result(env, 0, 0, EIO, 0, 0,\n                           now_ms() - started, STAGE_JNI);\n    }\n\n    PendingUrbOwnershipGuard pending_guard(slots);\n    UrbSlot& slot = slots[0];\n    int pending_count = 0;\n    int total = 0;\n    int submissions = 0;\n    int last_status = 0;\n    int last_actual = 0;\n    int last_kernel_errno = 0;\n    int64_t last_progress_ms = started;\n\n    auto finish = [&](bool success, int error_code, int stage,\n                      int kernel_errno = 0) -> jlongArray {\n        const int pending_at_stop = pending_count;\n        int drain_state = pending_count > 0 ? DRAIN_REAPED : DRAIN_NOT_NEEDED;\n        int drain_errno = 0;\n        if (pending_count > 0) {\n            if (!discard_and_reap_all(usb_fd.get(), slots, &pending_count, &drain_errno)) {\n                pending_guard.abandon_and_poison();\n                drain_state = DRAIN_FAILED;\n                success = false;\n                error_code = drain_errno != 0 ? drain_errno : EIO;\n                stage = STAGE_DRAIN;\n            }\n        }\n        if (success) {\n            env->SetByteArrayRegion(\n                destination, offset, length,\n                reinterpret_cast<const jbyte*>(slot.buffer.get()));\n            if (env->ExceptionCheck()) {\n                success = false;\n                error_code = EFAULT;\n                stage = STAGE_JNI;\n                env->ExceptionClear();\n            }\n        }\n        if (drain_state != DRAIN_FAILED) pending_guard.disarm();\n        const int64_t stopped = now_ms();\n        const int64_t last_age = (stopped - last_progress_ms) > 0\n            ? stopped - last_progress_ms : 0;\n        usb_fd.reset();\n        return make_result(\n            env, success ? 1 : 0, total, error_code, last_status, last_actual,\n            stopped - started, stage, submissions, pending_at_stop, last_age,\n            drain_state, drain_errno, backend_poisoned_noexcept() ? 1 : 0,\n            kernel_errno);\n    };\n\n    while (total < length) {\n        const int requested = length - total;\n        std::memset(slot.urb.get(), 0, sizeof(*slot.urb));\n        slot.urb->type = USBDEVFS_URB_TYPE_BULK;\n        slot.urb->endpoint = static_cast<unsigned char>(endpoint_address & 0xFF);\n        slot.urb->status = -1;\n        slot.urb->buffer = slot.buffer.get() + total;\n        slot.urb->buffer_length = requested;\n        slot.urb->usercontext = slot.urb.get();\n        slot.requested = requested;\n\n        int submit_result;\n        do {\n            submit_result = ioctl(usb_fd.get(), USBDEVFS_SUBMITURB, slot.urb.get());\n        } while (submit_result < 0 && errno == EINTR);\n\n        if (submit_result < 0) {\n            const int submit_errno = errno;\n            last_kernel_errno = submit_errno;\n            return finish(false, submit_errno, STAGE_SUBMIT, submit_errno);\n        }\n\n        slot.pending = true;\n        pending_count = 1;\n        ++submissions;\n        const int64_t wait_started = now_ms();\n\n        while (slot.pending) {\n            void* completed = nullptr;\n            if (ioctl(usb_fd.get(), USBDEVFS_REAPURBNDELAY, &completed) == 0) {\n                if (completed != slot.urb.get()) {\n                    return finish(false, EPROTO, STAGE_REAP);\n                }\n                slot.pending = false;\n                pending_count = 0;\n                last_status = slot.urb->status;\n                last_actual = slot.urb->actual_length;\n\n                if (last_status != 0) {\n                    const int status_errno = last_status < 0 ? -last_status : EIO;\n                    return finish(false, status_errno, STAGE_STATUS);\n                }\n                if (last_actual <= 0 || last_actual > requested) {\n                    return finish(false, EPROTO, STAGE_LENGTH);\n                }\n\n                total += last_actual;\n                last_progress_ms = now_ms();\n                break;\n            }\n\n            const int reap_errno = errno;\n            if (reap_errno == EINTR) continue;\n            if (reap_errno != EAGAIN) {\n                last_kernel_errno = reap_errno;\n                return finish(false, reap_errno, STAGE_REAP, reap_errno);\n            }\n            if (now_ms() - wait_started >= timeout_ms) {\n                return finish(false, ETIMEDOUT, STAGE_TIMEOUT);\n            }\n            usleep(REAP_POLL_SLEEP_US);\n        }\n    }\n\n    return finish(true, 0, STAGE_DONE, last_kernel_errno);\n}\n\nextern \"C\" JNIEXPORT jlongArray JNICALL\nJava_ru_forum_adbfastboottool_AdbNativeUsbfsIn_nativeReadExact(\n    JNIEnv* env, jobject /*thiz*/, jint fd, jint endpoint_address,\n    jbyteArray destination, jint offset, jint length, jint timeout_ms) {\n    try {\n        return native_adb_bulk_in_exact_impl(\n            env, fd, endpoint_address, destination, offset, length, timeout_ms);\n    } catch (const std::bad_alloc&) {\n        return make_result(env, 0, 0, ENOMEM, 0, 0, 0, STAGE_JNI);\n    } catch (...) {\n        return make_result(env, 0, 0, EIO, 0, 0, 0, STAGE_JNI);\n    }\n}\n\n".encode('utf-8')
    assert native_data.count(adb_in_native) == 1, "Unreviewed native ADB IN C++ change"
    native_data = native_data.replace(adb_in_native, b'', 1)
protected_native_hash = hashlib.sha1(
    b'blob ' + str(len(native_data)).encode() + b'\0' + native_data
).hexdigest()
assert protected_native_hash == 'fa4d1fc97e39c7ef598037281bae39fba0c48d97'
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
