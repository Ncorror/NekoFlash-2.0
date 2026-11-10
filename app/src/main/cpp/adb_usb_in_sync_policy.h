#pragma once

namespace nekoflash {

constexpr int kAdbSyncInMaxBytes = 16 * 1024;

constexpr bool is_valid_adb_sync_in_length(int length) noexcept {
    return length > 0 && length <= kAdbSyncInMaxBytes;
}

}  // namespace nekoflash
