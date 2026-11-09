#pragma once

namespace nekoflash {

constexpr int kAdbUsbInMaxRequestBytes = 1024 * 1024;

constexpr bool is_valid_adb_usb_in_transfer(
    int expected_length, int request_length) noexcept {
    return expected_length > 0 &&
        request_length >= expected_length &&
        request_length <= kAdbUsbInMaxRequestBytes;
}

}  // namespace nekoflash
