package ru.forum.adbfastboottool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class FirmwareSourceTest {
    @Test fun randomAccessReadsOutOfOrderWithoutReopeningOrCopy() {
        val file = Files.createTempFile("neko-sideload-source", ".zip").toFile()
        try {
            val bytes = ByteArray(384) { (it and 255).toByte() }
            file.writeBytes(bytes)
            FirmwareSource.WorkspaceFile(file).open().use { opened ->
                assertEquals(bytes.size.toLong(), opened.sizeBytes)
                val tail = ByteArray(64)
                opened.readFullyAt(320L, tail)
                assertArrayEquals(bytes.copyOfRange(320, 384), tail)
                val start = ByteArray(64)
                opened.readFullyAt(0L, start)
                assertArrayEquals(bytes.copyOfRange(0, 64), start)
            }
        } finally {
            file.delete()
        }
    }

    @Test(expected = IOException::class)
    fun rejectsBlocksBeyondFileBoundary() {
        val file = Files.createTempFile("neko-sideload-boundary", ".zip").toFile()
        try {
            file.writeBytes(ByteArray(64))
            FirmwareSource.WorkspaceFile(file).open().use {
                it.readFullyAt(48L, ByteArray(32))
            }
        } finally {
            file.delete()
        }
    }
}
