package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WorkspaceImportNamingTest {
    @Test fun providerNameCannotEscapeWorkspace() {
        val name = WorkspaceImportNaming.sanitizeImportedFileName("../dangerous/file?.img")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        val dir = Files.createTempDirectory("nekoflash-names").toFile()
        try {
            val file = WorkspaceImportNaming.uniqueTargetFile(dir, name)
            assertEquals(dir.canonicalFile, file.canonicalFile.parentFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun duplicateImportPreservesOriginalFile() {
        val dir = Files.createTempDirectory("nekoflash-import").toFile()
        try {
            dir.resolve("boot.img").writeText("original")
            dir.resolve("boot-1.img").writeText("another")
            val result = WorkspaceImportNaming.uniqueTargetFile(dir, "boot.img")
            assertEquals("boot-2.img", result.name)
            assertEquals("original", dir.resolve("boot.img").readText())
            assertEquals("another", dir.resolve("boot-1.img").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun longProviderNameIsNotSilentlyCutTo160Chars() {
        val name = "x".repeat(190) + ".img"
        assertEquals(name, WorkspaceImportNaming.sanitizeImportedFileName(name))
    }

    @Test fun dotSegmentsGetSafeFallback() {
        val sanitized = WorkspaceImportNaming.sanitizeImportedFileName("..")
        assertTrue(sanitized.startsWith("imported-"))
        assertFalse(sanitized == "..")
    }

    @Test(expected = IllegalArgumentException::class)
    fun directPathTraversalRejected() {
        WorkspaceImportNaming.uniqueTargetFile(Files.createTempDirectory("test-names").toFile(), "../escape")
    }
}
