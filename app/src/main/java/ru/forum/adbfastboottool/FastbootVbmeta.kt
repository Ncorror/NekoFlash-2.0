package ru.forum.adbfastboottool

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Creates a private, disposable vbmeta copy with AOSP Fastboot's AVB flags applied. */
internal object FastbootVbmeta {
    private const val MINIMUM_VBMETA_SIZE = 256L
    private const val FLAGS_LAST_BYTE_OFFSET = 123L
    private const val DISABLE_VERITY = 0x01
    private const val DISABLE_VERIFICATION = 0x02

    fun createPatchedCopy(
        source: File,
        temporaryDirectory: File,
        disableVerity: Boolean,
        disableVerification: Boolean,
    ): File {
        require(disableVerity || disableVerification) { "At least one AVB flag must be selected" }
        require(source.isFile && source.canRead()) { "VBMeta image is missing or unreadable: ${source.path}" }
        require(source.length() >= MINIMUM_VBMETA_SIZE) { "VBMeta image is too small to contain the AVB header" }

        val header = ByteArray(4)
        source.inputStream().use { input ->
            require(input.read(header) == header.size) { "Cannot read VBMeta magic" }
        }
        require(header.contentEquals(byteArrayOf(0x41, 0x56, 0x42, 0x30))) {
            "Image does not contain the AVB0 VBMeta magic; refusing to rewrite it"
        }

        require(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs()) {
            "Cannot create private VBMeta staging directory: $temporaryDirectory"
        }
        val patched = Files.createTempFile(temporaryDirectory.toPath(), "nekoflash-vbmeta-", ".img").toFile()
        try {
            Files.copy(source.toPath(), patched.toPath(), StandardCopyOption.REPLACE_EXISTING)
            RandomAccessFile(patched, "rw").use { image ->
                image.seek(FLAGS_LAST_BYTE_OFFSET)
                val existing = image.readUnsignedByte()
                val requested = (if (disableVerity) DISABLE_VERITY else 0) or
                    (if (disableVerification) DISABLE_VERIFICATION else 0)
                image.seek(FLAGS_LAST_BYTE_OFFSET)
                image.write(existing or requested)
            }
            return patched
        } catch (error: Exception) {
            patched.delete()
            throw error
        }
    }
}
