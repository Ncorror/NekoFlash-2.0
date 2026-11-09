#pragma once

#include <cerrno>

namespace nekoflash {

constexpr int kAdbUsbInMaxZeroByteBusRetries = 3;
constexpr int kAdbUsbInUrbChunkBytes = 4 * 1024;

constexpr int adb_usb_in_request_bytes(int remaining) noexcept {
    if (remaining <= 0) return 0;
    return remaining > kAdbUsbInUrbChunkBytes ? kAdbUsbInUrbChunkBytes : remaining;
}

constexpr int kAdbUsbInRetryBaseDelayUs = 150 * 1000;

constexpr bool is_adb_usb_in_retryable_status(int status) noexcept {
    return status == -EPROTO || status == -EILSEQ || status == -ETIME;
}

constexpr bool should_retry_adb_usb_in(
    int status, int actual_length, int retries_used) noexcept {
    return actual_length == 0 &&
        retries_used >= 0 &&
        retries_used < kAdbUsbInMaxZeroByteBusRetries &&
        is_adb_usb_in_retryable_status(status);
}

constexpr int adb_usb_in_retry_delay_us(int retry_number) noexcept {
    if (retry_number <= 0) return 0;
    if (retry_number == 1) return kAdbUsbInRetryBaseDelayUs;
    if (retry_number == 2) return kAdbUsbInRetryBaseDelayUs * 2;
    return kAdbUsbInRetryBaseDelayUs * 4;
}

}  // namespace nekoflash
