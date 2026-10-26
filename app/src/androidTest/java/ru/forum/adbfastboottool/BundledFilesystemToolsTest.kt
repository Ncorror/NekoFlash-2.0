package ru.forum.adbfastboottool

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs as an ordinary installed app UID. Outputs are independently decoded/fsck'ed on CI. */
@RunWith(AndroidJUnit4::class)
class BundledFilesystemToolsTest {
    private fun generate(type: String, options: Set<String> = emptySet()) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val tools = BundledFilesystemTools.fromDirectory(nativeDir)
        tools.values.forEach {
            assertEquals(nativeDir, it.parentFile)
            assertTrue("Installed generator missing: $it", it.isFile && it.canExecute())
            assertFalse(it.canonicalPath.startsWith(context.dataDir.canonicalPath + "/"))
        }
        context.assets.open("filesystem-tools/manifest.json").use { assertTrue(it.read() >= 0) }
        val work = File(context.cacheDir, "mkfs-instrumentation-$type").also { it.mkdirs() }
        try {
            val bytes = (if (type == "ext4") 32L else 128L) * 1024 * 1024
            val image = FastbootFilesystemFormatter(tools, AndroidImageExtents::nextData)
                .generate(FilesystemSpec(type, bytes, options = options), work) {}
            assertEquals(bytes, AndroidSparseImage.inspect(image).expandedBytes)
            assertTrue(image.length() < bytes)
            assertEquals(listOf(image), work.listFiles()!!.toList())
            val outputs = File(context.filesDir, "mkfs-test-output").also { it.mkdirs() }
            image.copyTo(File(outputs, "$type-${if (options.isEmpty()) "default" else "options"}.sparse"), overwrite = true)
        } finally {
            work.deleteRecursively()
        }
    }

    @Test fun installedExt4CreatesImage() = generate("ext4")
    @Test fun installedF2fsCreatesImage() = generate("f2fs")
    @Test fun ext4OptionsCreateImage() = generate("ext4", setOf("casefold", "projid"))
    @Test fun f2fsOptionsCreateImage() = generate("f2fs", setOf("casefold", "projid", "compress"))
}
