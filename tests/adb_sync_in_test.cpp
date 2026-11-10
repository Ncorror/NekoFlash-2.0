#include "../app/src/main/cpp/adb_sync_in.h"
#include <cassert>
#include <initializer_list>

int main() {
    for (int error : {EPROTO, ETIMEDOUT, EPIPE, ENODEV, EINTR}) {
        int calls = 0;
        int ticks = 0;
        auto result = nekoflash::observe_sync_in([&]() {
            ++calls;
            errno = error;
            return -1;
        }, [&]() -> int64_t { errno = EDOM; return ++ticks * 100; });
        assert(calls == 1 && result.result == -1 && result.error == error);
        assert(result.elapsed_ns == 100);
    }
    for (int bytes : {0, 512, 16384}) {
        auto result = nekoflash::observe_sync_in([&]() {
            errno = EPROTO; // stale errno must not contaminate success/ZLP
            return bytes;
        }, []() -> int64_t { return 0; });
        assert(result.result == bytes && result.error == 0);
    }
}
