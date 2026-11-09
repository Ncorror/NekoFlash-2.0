#include "../app/src/main/cpp/transfer_deadline.h"
#include "../app/src/main/cpp/adb_usb_in_retry_policy.h"
#include <cassert>
#include <iostream>

int main() {
    using nekoflash::TransferDeadline;
    assert(TransferDeadline::valid(30000, 0));
    assert(TransferDeadline::valid(30000, 90000));
    assert(!TransferDeadline::valid(0, 0));
    assert(!TransferDeadline::valid(30000, -1));
    assert(!TransferDeadline::valid(30000, 1000));

    // Drive the same clock policy used by native submit/reap through a 40-minute
    // transfer. Progress must renew only the stall timer, never create a total cap.
    const int64_t start = 9000000000LL;
    TransferDeadline ongoing(start, 30000, 0);
    for (int64_t elapsed = 10000; elapsed <= 2400000; elapsed += 10000) {
        assert(!ongoing.stalled(start + elapsed));
        assert(!ongoing.hard_expired(start + elapsed));
        ongoing.progress(start + elapsed);
    }
    assert(!ongoing.stalled(start + 2430000));
    assert(ongoing.stalled(start + 2430001));
    assert(!ongoing.hard_expired(start + 2430001));

    TransferDeadline first_urb(start, 30000, 0);
    assert(first_urb.stalled(start + 30001));
    TransferDeadline explicit_cap(start, 30000, 90000);
    explicit_cap.progress(start + 89999);
    assert(!explicit_cap.stalled(start + 90001));
    assert(explicit_cap.hard_expired(start + 90001));
    using nekoflash::adb_usb_in_request_bytes;
    using nekoflash::adb_usb_in_retry_delay_us;
    using nekoflash::should_retry_adb_usb_in;
    assert(should_retry_adb_usb_in(-EPROTO, 0, 0));
    assert(should_retry_adb_usb_in(-EILSEQ, 0, 1));
    assert(should_retry_adb_usb_in(-ETIME, 0, 2));
    assert(!should_retry_adb_usb_in(-EPROTO, 0, 3));
    assert(!should_retry_adb_usb_in(-EPROTO, 1, 0));
    assert(!should_retry_adb_usb_in(-EPIPE, 0, 0));
    assert(!should_retry_adb_usb_in(-EOVERFLOW, 0, 0));
    assert(!should_retry_adb_usb_in(-ENODEV, 0, 0));
    assert(!should_retry_adb_usb_in(-ESHUTDOWN, 0, 0));
    assert(adb_usb_in_request_bytes(0) == 0);
    assert(adb_usb_in_request_bytes(1) == 1);
    assert(adb_usb_in_request_bytes(4095) == 4095);
    assert(adb_usb_in_request_bytes(4096) == 4096);
    assert(adb_usb_in_request_bytes(4097) == 4096);
    assert(adb_usb_in_request_bytes(16384) == 4096);
    assert(adb_usb_in_retry_delay_us(0) == 0);
    assert(adb_usb_in_retry_delay_us(1) == 150000);
    assert(adb_usb_in_retry_delay_us(2) == 300000);
    assert(adb_usb_in_retry_delay_us(3) == 600000);

    std::cout << "PASS native deadlines + ADB IN zero-byte EPROTO-class bounded retry policy\n";
}
