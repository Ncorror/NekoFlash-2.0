package ru.forum.adbfastboottool

/**
 * A Fastboot download DATA OUT may complete with INFO packets followed by a
 * final OKAY/FAIL. Retain the established 30-second total post-DATA budget
 * while making each USB read cancellable; no retransmission of a command or
 * any part of the DATA image is allowed.
 */
internal object FastbootPostDownloadWaitPolicy {
    const val READ_SLICE_MS = 2_000
    const val FINAL_TIMEOUT_MS = 30_000L
}
