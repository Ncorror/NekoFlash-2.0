#pragma once

#include <cerrno>

namespace nekoflash {

constexpr int kAdbUsbInMaxZeroByteBusRetries = 3;
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
    return retry_number <= 0
        ? 0
        : kAdbUsbInRetryBaseDelayUs * retry_number;
}

}  // namespace nekoflash
