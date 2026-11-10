#pragma once

#include <cerrno>
#include <cstdint>

namespace nekoflash {
struct SyncInResult {
    int result;
    int error;
    int64_t elapsed_ns;
};

// One synchronous syscall only. In particular EINTR is not replayed: BULK
// provides no actual_length on failure and may already have consumed bytes.
template <typename Call, typename Clock>
SyncInResult observe_sync_in(Call call, Clock clock) {
    const auto started = clock();
    const int result = call();
    const int error = result < 0 ? errno : 0; // before clock/JNI/logging
    return {result, error, clock() - started};
}
}  // namespace nekoflash
