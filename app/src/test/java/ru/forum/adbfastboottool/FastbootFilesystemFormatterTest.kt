package ru.forum.adbfastboottool

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FastbootFilesystemFormatterTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun tool(name: String): File = listOf("/usr/sbin", "/sbin", "/usr/bin").map { File(it, name) }
        .firstOrNull { it.isFile && it.canExecute() } ?: error("Required integration-test tool is missing: $name")
    private fun validate(type: String, bytes: Long, options: Set<String> = emptySet()) {
        val mkfs = tool(if (type == "ext4") "mke2fs" else "mkfs.f2fs")
        val generator = FastbootFilesystemFormatter(mapOf(type to mkfs))
        val sparse = generator.generate(FilesystemSpec(type, bytes, options = options), temporary.root) {}
        val raw = File(temporary.root, "verified.raw")
        HostImageFixtures.expand(sparse, raw)
        val fsck = tool(if (type == "ext4") "e2fsck" else "fsck.f2fs")
        val args = if (type == "ext4") listOf(fsck.path, "-f", "-n", raw.path) else listOf(fsck.path, "-f", raw.path)
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        assertTrue("Sparse image should be smaller than its expanded filesystem", sparse.length() < bytes)
        assertTrue(temporary.root.listFiles()!!.none { it.name.startsWith("filesystem-") && it.extension == "raw" })
    }
    @Test fun realExt4RoundTripsThroughSparseAndPassesE2fsck() = validate("ext4", 32L * 1024 * 1024)
    @Test fun realF2fsRoundTripsThroughSparseAndPassesFsck() = validate("f2fs", 128L * 1024 * 1024)
    @Test fun ext4CasefoldAndProjectInodesAreAcceptedByRealTools() = validate("ext4", 32L * 1024 * 1024, setOf("casefold", "projid"))
    @Test fun f2fsAllHostOptionsProduceValidFilesystem() = validate("f2fs", 128L * 1024 * 1024, setOf("casefold", "projid", "compress"))
    @Test fun missingToolFailsBeforeCreatingAnyImages() {
        assertThrows(IllegalStateException::class.java) {
            FastbootFilesystemFormatter(emptyMap()).generate(FilesystemSpec("ext4", 32L * 1024 * 1024), temporary.root) {}
        }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
    @Test fun unsupportedFilesystemNeverFallsBackToEraseOrExt4() {
        assertThrows(IllegalArgumentException::class.java) {
            FastbootFilesystemFormatter(emptyMap()).generate(FilesystemSpec("ntfs", 1024), temporary.root) {}
        }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
    @Test fun failingMkfsCleansTemporaryFiles() {
        assertThrows(IllegalStateException::class.java) {
            FastbootFilesystemFormatter(mapOf("ext4" to File("/bin/false")))
                .generate(FilesystemSpec("ext4", 32L * 1024 * 1024), temporary.root) {}
        }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationCleansTemporaryFiles() {
        assertThrows(InterruptedException::class.java) {
            FastbootFilesystemFormatter(mapOf("ext4" to tool("mke2fs")))
                .generate(FilesystemSpec("ext4", 32L * 1024 * 1024), temporary.root) { throw InterruptedException() }
        }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationWhileGeneratorRunsTerminatesItBeforeRemovingImages() {
        val pid = File(temporary.root, "generator.pid")
        val script = File(temporary.root, "slow-mkfs").also {
            it.writeText("#!/bin/sh\necho \$\$ > '${pid.path}'\nexec /bin/sleep 30\n")
            check(it.setExecutable(true))
        }
        var checks = 0
        val start = System.nanoTime()
        assertThrows(InterruptedException::class.java) {
            FastbootFilesystemFormatter(mapOf("ext4" to script))
                .generate(FilesystemSpec("ext4", 32L * 1024 * 1024), temporary.root) {
                    if (++checks >= 2) throw InterruptedException()
                }
        }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000)
        assertFalse(ProcessHandle.of(pid.readText().trim().toLong()).map { it.isAlive }.orElse(false))
        assertTrue(temporary.root.listFiles()!!.none { it.name.startsWith("filesystem-") || it.name.startsWith("mkfs-") })
    }
}
