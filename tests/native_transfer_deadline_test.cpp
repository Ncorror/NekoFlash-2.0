#include "../app/src/main/cpp/transfer_deadline.h"
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
    std::cout << "PASS native deadlines: long progress, stalled first/next URB, optional cap, 64-bit clock\n";
}
