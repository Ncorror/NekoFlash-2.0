package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

internal data class FilesystemSpec(val type: String, val sizeBytes: Long, val eraseBlockBytes: Long = 0,
    val logicalBlockBytes: Long = 0, val options: Set<String> = emptySet())

internal fun interface FilesystemFormatter {
    /** Creates a private, validated sparse image. Never erases or writes a USB device. */
    fun generate(spec: FilesystemSpec, directory: File, checkCancelled: () -> Unit): File
}

/** Uses real filesystem tools on the host. It never substitutes erase for a failed mkfs. */
internal class FastbootFilesystemFormatter(
    private val tools: Map<String, File>,
    private val nextData: (RandomAccessFile, Long) -> Long? = { _, offset -> offset },
    private val log: (String) -> Unit = {}
) : FilesystemFormatter {
    override fun generate(spec: FilesystemSpec, directory: File, checkCancelled: () -> Unit): File {
        require(spec.type in setOf("ext4", "f2fs")) { "Unsupported filesystem: ${spec.type}" }
        val tool = tools[spec.type]?.takeIf { it.isFile && it.canExecute() }
            ?: error("Filesystem generator unavailable on this Android host: ${spec.type} (mke2fs / mkfs.f2fs). No erase was sent.")
        val size = spec.sizeBytes / 4096 * 4096
        require(size >= 4096 && size / 4096 <= 0xffff_ffffL) { "Filesystem size cannot be encoded in Android sparse: $size" }
        require(directory.isDirectory)
        val raw = File.createTempFile("filesystem-", ".raw", directory)
        val sparse = File.createTempFile("filesystem-", ".sparse", directory)
        val processLog = File.createTempFile("mkfs-", ".log", directory)
        var profile: File? = null
        var keep = false
        try {
            if (spec.type == "ext4") {
                profile = File.createTempFile("mke2fs-", ".conf", directory)
                profile.writeText(EXT4_PROFILE)
            }
            RandomAccessFile(raw, "rw").use { it.setLength(size) }
            val args = arguments(tool, spec, raw, size)
            log("Host filesystem: ${spec.type}, $size bytes, tool=${tool.absolutePath}")
            checkCancelled()
            val process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(processLog).apply {
                environment()["LC_ALL"] = "C"
                profile?.let { environment()["MKE2FS_CONFIG"] = it.absolutePath }
            }.start()
            try {
                process.outputStream.close()
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) checkCancelled()
                checkCancelled()
                processLog.bufferedReader().useLines { lines -> lines.forEach { checkCancelled(); log("[mkfs] $it") } }
                check(process.exitValue() == 0) { "${tool.name} failed (${process.exitValue()}): ${tail(processLog)}" }
            } finally {
                if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
            }
            require(raw.length() == size) { "Filesystem tool changed image length unexpectedly" }
            verifySuperblock(raw, spec.type, size)
            AndroidSparseImage.fromRaw(raw, sparse, nextData, checkCancelled)
            require(AndroidSparseImage.inspect(sparse, checkCancelled).expandedBytes == size)
            keep = true
            return sparse
        } finally {
            raw.delete()
            processLog.delete()
            profile?.delete()
            if (!keep) sparse.delete()
        }
    }

    internal fun arguments(tool: File, spec: FilesystemSpec, image: File, size: Long): List<String> {
        if (spec.type == "f2fs") return buildList {
            addAll(listOf(tool.absolutePath, "-f", "-t", "0", "-g", "android"))
            if ("projid" in spec.options) addAll(listOf("-O", "project_quota,extra_attr"))
            if ("casefold" in spec.options) addAll(listOf("-O", "casefold", "-C", "utf8"))
            if ("compress" in spec.options) addAll(listOf("-O", "compression,extra_attr"))
            addAll(listOf(image.absolutePath, (size / 512).toString()))
        }
        val features = "none,has_journal,extent,huge_file,flex_bg,uninit_bg,dir_nlink,extra_isize," +
            "sparse_super,large_file,filetype,resize_inode,dir_index,ext_attr" +
            if ("casefold" in spec.options) ",casefold" else ""
        val extended = buildList {
            // Fully initialize our local image before turning holes/zeroes into sparse chunks.
            add("lazy_itable_init=0"); add("lazy_journal_init=0"); add("root_owner=0:0")
            if (spec.eraseBlockBytes > 0 && spec.logicalBlockBytes > 0) {
                val stride = maxOf(2L, spec.logicalBlockBytes / 4096)
                add("stride=$stride"); add("stripe-width=${maxOf(stride, spec.eraseBlockBytes / 4096)}")
            }
            if ("casefold" in spec.options) add("encoding=utf8")
        }.joinToString(",")
        return listOf(tool.absolutePath, "-F", "-t", "ext4", "-b", "4096", "-I",
            if ("projid" in spec.options) "512" else "256", "-O", features, "-E", extended,
            image.absolutePath, (size / 4096).toString())
    }

    private fun verifySuperblock(file: File, type: String, size: Long) = RandomAccessFile(file, "r").use { input ->
        val bytes = ByteArray(1024)
        input.seek(1024); input.readFully(bytes)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (type == "ext4") {
            require(b.getShort(56).toInt() and 0xffff == 0xef53) { "Generated image is not ext4" }
            val count = b.getInt(4).toLong() and 0xffff_ffffL
            require(b.getInt(24) == 2 && Math.multiplyExact(count, 4096L) == size) { "ext4 geometry mismatch" }
        } else {
            require(b.getInt(0) == 0xf2f52010.toInt()) { "Generated image is not F2FS" }
            require(b.getInt(16) == 12 && b.getLong(36) > 0 && Math.multiplyExact(b.getLong(36), 4096L) <= size) { "F2FS geometry mismatch" }
        }
    }

    private fun tail(file: File): String = RandomAccessFile(file, "r").use {
        it.seek(maxOf(0L, it.length() - 4096))
        val bytes = ByteArray((it.length() - it.filePointer).toInt()); it.readFully(bytes)
        bytes.toString(Charsets.UTF_8).trim()
    }

    companion object {
        // Keep image defaults independent of the formatting phone's system configuration.
        private const val EXT4_PROFILE = "[defaults]\n" +
            "base_features = sparse_super,large_file,filetype,resize_inode,dir_index,ext_attr\n" +
            "default_mntopts = acl,user_xattr\nenable_periodic_fsck = 0\n" +
            "blocksize = 4096\ninode_size = 256\ninode_ratio = 16384\n" +
            "[fs_types]\next4 = {\n inode_ratio = 16384\n}\n" +
            "small = {\n inode_ratio = 4096\n}\nfloppy = {\n inode_ratio = 8192\n}\n" +
            "big = {\n inode_ratio = 32768\n}\nhuge = {\n inode_ratio = 65536\n}\n"
    }
}
