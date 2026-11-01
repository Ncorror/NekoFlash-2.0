package ru.forum.adbfastboottool

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * A file source is independent from the Download/NekoFlash workspace.
 * Sideload needs seekable blocks. SAF pipes are rejected before USB writes.
 */
internal sealed interface FirmwareSource {
    val displayName: String
    val sizeHint: Long?
    fun open(resolver: ContentResolver? = null): Opened

    interface Opened : Closeable {
        val sizeBytes: Long
        /** Read-only descriptor borrowed for the duration of this Opened owner. */
        val nativeFd: Int? get() = null
        fun readFullyAt(offset: Long, target: ByteArray)
    }

    data class WorkspaceFile(val file: File) : FirmwareSource {
        override val displayName: String get() = file.name
        override val sizeHint: Long get() = file.length()
        override fun open(resolver: ContentResolver?): Opened {
            if (!file.isFile || !file.canRead()) throw IOException("Local file is not readable")
            val raf = RandomAccessFile(file, "r")
            return object : Opened {
                override val sizeBytes: Long = raf.length()
                override fun readFullyAt(offset: Long, target: ByteArray) {
                    if (offset < 0L || offset > sizeBytes ||
                        target.size.toLong() > sizeBytes - offset) {
                        throw IOException("Block outside local file")
                    }
                    raf.seek(offset)
                    raf.readFully(target)
                }
                override fun close() = raf.close()
            }
        }
    }

    data class SafDocument(
        val uri: Uri,
        override val displayName: String,
        override val sizeHint: Long? = null
    ) : FirmwareSource {
        override fun open(resolver: ContentResolver?): Opened {
            val provider = resolver ?: throw IOException("Android ContentResolver unavailable")
            val descriptor = provider.openFileDescriptor(uri, "r")
                ?: throw IOException("Document provider returned no read descriptor")
            try {
                val stream = FileInputStream(descriptor.fileDescriptor)
                try {
                    val channel = stream.channel
                    channel.position(0L) // fail if provider returns a pipe
                    val size = descriptor.statSize.takeIf { it > 0L } ?: channel.size()
                    if (size <= 0L) throw IOException("Empty or size-unknown document")
                    return object : Opened {
                        override val sizeBytes: Long = size
                        override val nativeFd: Int = descriptor.fd
                        override fun readFullyAt(offset: Long, target: ByteArray) {
                            if (offset < 0L || offset > size ||
                                target.size.toLong() > size - offset) {
                                throw IOException("Recovery block outside document")
                            }
                            val bytes = ByteBuffer.wrap(target)
                            var read = 0
                            while (bytes.hasRemaining()) {
                                val n = channel.read(bytes, offset + read.toLong())
                                if (n <= 0) throw IOException("Document ended while reading block")
                                read += n
                            }
                        }
                        override fun close() {
                            try {
                                stream.close()
                            } finally {
                                descriptor.close()
                            }
                        }
                    }
                } catch (error: Exception) {
                    stream.close()
                    throw error
                }
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
        }
    }
}
