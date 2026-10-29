package ru.forum.adbfastboottool

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Avoid blocking indefinitely inside InputStream.read() on older Android versions.
 *
 * Some ContentProviders return a pipe whose writer remains open without producing
 * more bytes. Closing a FileInputStream from another thread does not reliably
 * interrupt that already-blocked read on API 26. Poll first, with a bounded wait,
 * so an import cancellation can be observed even when the provider is stuck.
 *
 * This guards the app-owned descriptor; it does not change the source provider,
 * seek position, file contents, or the importer publication/rollback rules.
 */
internal class CancellableProviderInputStream(
    source: InputStream,
    private val descriptor: FileDescriptor,
    private val checkCancellation: () -> Unit,
) : FilterInputStream(source) {
    override fun read(): Int {
        awaitReadable()
        return super.read()
    }

    override fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) return 0
        awaitReadable()
        return super.read(buffer, offset, length)
    }

    private fun awaitReadable() {
        val pollFd = StructPollfd().apply {
            fd = descriptor
            events = OsConstants.POLLIN.toShort()
        }
        while (true) {
            checkCancellation()
            pollFd.revents = 0
            val events = try {
                Os.poll(arrayOf(pollFd), POLL_INTERVAL_MS)
            } catch (error: ErrnoException) {
                checkCancellation()
                if (error.errno == OsConstants.EINTR) continue
                throw IOException("Cannot check import source readiness", error)
            }
            checkCancellation()
            if (events == 0) continue
            if ((pollFd.revents.toInt() and OsConstants.POLLNVAL) != 0) {
                throw IOException("Import source descriptor is no longer valid")
            }
            // POLLIN covers buffered data and EOF/HUP on pipes; the underlying
            // AssetFileDescriptor stream still owns all actual reads and bounds.
            return
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100
    }
}
