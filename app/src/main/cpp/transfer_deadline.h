#pragma once

#include <cstdint>

namespace nekoflash {

// A zero total deadline allows large transfers to keep making progress.
// The completion-stall timeout remains mandatory; cancellation is independent.
class TransferDeadline {
public:
    TransferDeadline(int64_t started, int stall_ms, int hard_ms)
        : started_(started), last_progress_(started), stall_ms_(stall_ms), hard_ms_(hard_ms) {}

    static bool valid(int stall_ms, int hard_ms) {
        return stall_ms > 0 && (hard_ms == 0 || hard_ms >= stall_ms);
    }

    void progress(int64_t now) { last_progress_ = now; }
    bool hard_expired(int64_t now) const {
        return hard_ms_ > 0 && now - started_ > hard_ms_;
    }
    bool stalled(int64_t now) const { return now - last_progress_ > stall_ms_; }

private:
    int64_t started_;
    int64_t last_progress_;
    int stall_ms_;
    int hard_ms_;
};

}  // namespace nekoflash
