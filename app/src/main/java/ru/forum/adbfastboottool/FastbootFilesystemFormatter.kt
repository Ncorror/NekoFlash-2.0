package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

internal data class FilesystemSpec(
    val type: String,
    val sizeBytes: Long,
    val eraseBlockBytes: Long = 0,
    val logicalBlockBytes: Long = 0,
    val options: Set<String> = emptySet()
)

internal fun interface FilesystemFormatter {
    /** Creates a private, validated sparse image. Never erases or writes a USB device. */
    fun generate(spec: FilesystemSpec, directory: File, checkCancelled: () -> Unit): File
}

/**
 * Reproduces the sparse filesystem images produced by desktop fastboot's AOSP generators.
 *
 * [tools] and [nextData] remain constructor-compatible with the installed-tool evidence path;
 * formatting itself no longer depends on raw-image -> generic-sparse conversion.
 */
internal class FastbootFilesystemFormatter(
    @Suppress("unused") private val tools: Map<String, File>,
    @Suppress("unused") private val nextData: (RandomAccessFile, Long) -> Long? = { _, offset -> offset },
    private val log: (String) -> Unit = {}
) : FilesystemFormatter {
    override fun generate(spec: FilesystemSpec, directory: File, checkCancelled: () -> Unit): File {
        require(spec.type in setOf("ext4", "f2fs")) { "Unsupported filesystem: ${spec.type}" }
        val size = spec.sizeBytes / 4096 * 4096
        require(size >= 4096 && size / 4096 <= 0xffff_ffffL) {
            "Filesystem size cannot be encoded in Android sparse: $size"
        }
        require(directory.isDirectory)

        return when (spec.type) {
            "ext4" -> generateExt4(spec, size, directory, checkCancelled)
            "f2fs" -> generateF2fs(spec, size, directory, checkCancelled)
            else -> error("unreachable")
        }
    }

    private fun generateExt4(
        spec: FilesystemSpec,
        size: Long,
        directory: File,
        checkCancelled: () -> Unit
    ): File {
        val random = SecureRandom()
        val request = FastbootExt4.Request(
            sizeBytes = size,
            eraseBlockSize = spec.eraseBlockBytes,
            logicalBlockSize = spec.logicalBlockBytes,
            projectIds = "projid" in spec.options,
            casefold = "casefold" in spec.options,
            uuid = ByteArray(16).also(random::nextBytes),
            hashSeed = ByteArray(16).also(random::nextBytes),
            now = System.currentTimeMillis() / 1000L
        )
        log("Host filesystem: ext4, $size bytes, generator=AOSP mke2fs android_sparse compatible")
        return writeSparse(directory, size, checkCancelled) {
            FastbootExt4.image(request).getOrElse {
                throw IllegalStateException("AOSP-compatible ext4 sparse generation failed: ${it.message}", it)
            }
        }
    }

    private fun generateF2fs(
        spec: FilesystemSpec,
        size: Long,
        directory: File,
        checkCancelled: () -> Unit
    ): File {
        val random = SecureRandom()
        val request = FastbootF2fs.Request(
            sizeBytes = size,
            projectIds = "projid" in spec.options,
            casefold = "casefold" in spec.options,
            compression = "compress" in spec.options,
            uuid = ByteArray(16).also(random::nextBytes),
            now = System.currentTimeMillis() / 1000L,
            checkpointVersion = ((random.nextInt() and Int.MAX_VALUE) or 1).toLong(),
            kernelRelease = System.getProperty("os.version").orEmpty()
        )
        log("Host filesystem: f2fs, $size bytes, generator=AOSP make_f2fs -S compatible")
        return writeSparse(directory, size, checkCancelled) {
            FastbootF2fs.image(request).getOrElse {
                throw IllegalStateException("AOSP-compatible F2FS sparse generation failed: ${it.message}", it)
            }
        }
    }

    private fun writeSparse(
        directory: File,
        size: Long,
        checkCancelled: () -> Unit,
        image: () -> ByteArray
    ): File {
        val sparse = File.createTempFile("filesystem-", ".sparse", directory)
        var keep = false
        try {
            checkCancelled()
            val bytes = image()
            checkCancelled()
            sparse.writeBytes(bytes)
            require(AndroidSparseImage.inspect(sparse, checkCancelled).expandedBytes == size) {
                "Generated sparse filesystem geometry mismatch"
            }
            keep = true
            return sparse
        } finally {
            if (!keep) sparse.delete()
        }
    }
}
