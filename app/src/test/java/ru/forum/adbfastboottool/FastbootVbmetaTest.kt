package ru.forum.adbfastboottool

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootVbmetaTest {
    @Test fun appliesBothAvbFlagsToPrivateCopyAndPreservesOriginal() {
        val directory = Files.createTempDirectory("nekoflash-vbmeta").toFile()
        try {
            val originalBytes = ByteArray(512) { (it and 0xff).toByte() }.apply {
                this[0] = 0x41
                this[1] = 0x56
                this[2] = 0x42
                this[3] = 0x30
                this[123] = 0x20
            }
            val source = File(directory, "vbmeta.img").apply { writeBytes(originalBytes) }
            val staging = File(directory, "private-stage")
            val patched = FastbootVbmeta.createPatchedCopy(
                source, staging, disableVerity = true, disableVerification = true,
            )
            assertTrue(patched.isFile)
            assertTrue(patched.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath()))
            assertEquals(0x23, patched.readBytes()[123].toInt() and 0xff)
            assertArrayEquals(originalBytes, source.readBytes())
            patched.delete()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun appliesOnlyRequestedFlag() {
        val directory = Files.createTempDirectory("nekoflash-vbmeta").toFile()
        try {
            val source = File(directory, "vbmeta.img").apply {
                writeBytes(ByteArray(256).apply {
                    this[0] = 0x41
                    this[1] = 0x56
                    this[2] = 0x42
                    this[3] = 0x30
                })
            }
            val patched = FastbootVbmeta.createPatchedCopy(
                source, File(directory, "stage"), disableVerity = false, disableVerification = true,
            )
            assertEquals(0x02, patched.readBytes()[123].toInt() and 0xff)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsNonVbmetaInputAndSmallImages() {
        val directory = Files.createTempDirectory("nekoflash-vbmeta").toFile()
        try {
            val wrongMagic = File(directory, "wrong.img").apply { writeBytes(ByteArray(512)) }
            assertTrue(runCatching {
                FastbootVbmeta.createPatchedCopy(wrongMagic, File(directory, "stage"), true, false)
            }.exceptionOrNull() is IllegalArgumentException)
            val short = File(directory, "short.img").apply { writeBytes(byteArrayOf(0x41, 0x56, 0x42, 0x30)) }
            assertTrue(runCatching {
                FastbootVbmeta.createPatchedCopy(short, File(directory, "stage"), true, false)
            }.exceptionOrNull() is IllegalArgumentException)
        } finally {
            directory.deleteRecursively()
        }
    }
}
