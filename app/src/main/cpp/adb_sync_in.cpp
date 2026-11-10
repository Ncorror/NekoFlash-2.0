#include "adb_sync_in.h"
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <time.h>
#include <memory>
#include <new>

namespace {
int64_t monotonic_ns() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}
}

// Mirrors Android 16 UsbDeviceConnection JNI/libusbhost's synchronous IN
// allocation/copy contract. No URBs, reaping, retries, reset or fd ownership.
extern "C" JNIEXPORT jlongArray JNICALL
Java_ru_forum_adbfastboottool_AdbSyncUsbIn_nativeRead(
    JNIEnv* env, jobject, jint fd, jint endpoint, jbyteArray destination,
    jint offset, jint length, jint timeout_ms) {
    nekoflash::SyncInResult observed{-1, EINVAL, 0};
    jlong stage = 0; // preflight, not a kernel errno
    if (destination != nullptr && fd >= 0 && (endpoint & 0x80) != 0 &&
        (endpoint & 0x0f) != 0 && offset >= 0 && length > 0 && timeout_ms > 0 &&
        offset <= env->GetArrayLength(destination) &&
        length <= env->GetArrayLength(destination) - offset) {
        std::unique_ptr<jbyte[]> buffer(new (std::nothrow) jbyte[length]);
        if (!buffer) {
            observed.error = ENOMEM;
        } else {
            usbdevfs_bulktransfer request{};
            request.ep = static_cast<unsigned int>(endpoint);
            request.len = static_cast<unsigned int>(length);
            request.timeout = static_cast<unsigned int>(timeout_ms);
            request.data = buffer.get();
            stage = 1; // actual USBDEVFS_BULK result
            observed = nekoflash::observe_sync_in(
                [&]() { return ioctl(fd, USBDEVFS_BULK, &request); }, monotonic_ns);
            if (observed.result > length) {
                stage = 2; // impossible completion; do not copy out of bounds
                observed.error = EOVERFLOW;
                observed.result = -1;
            } else if (observed.result > 0) {
                env->SetByteArrayRegion(destination, offset, observed.result, buffer.get());
                if (env->ExceptionCheck()) return nullptr;
            }
        }
    }
    const jlong values[] = {observed.result, observed.error, observed.elapsed_ns, stage};
    jlongArray result = env->NewLongArray(4);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}
