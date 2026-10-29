package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Host preparation only. APK selection and the ADB transport remain in AdbProtocol. */
internal object PackageArchiveExtractor {
    data class Entry(val file: File, val entryName: String, val obb: Boolean)
    private data class Planned(val entry: ZipEntry, val name: String, val obb: Boolean)

    /** Hold the private cache lock until installation and cleanup finish. */
    inline fun <T> withWorkspace(cacheRoot: File, block: (File) -> T): T {
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) throw IOException("Cannot create package cache")
        java.nio.channels.FileChannel.open(File(cacheRoot, "lock").toPath(),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE).use { channel ->
            val lock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                ?: throw IOException("Package cache is in use")
            lock.use {
                cacheRoot.listFiles()?.filter { it.name == "pending" || it.name.startsWith("pkg-") }?.forEach {
                    if (!it.deleteRecursively()) throw IOException("Cannot clean interrupted package preparation")
                }
                val directory = File(cacheRoot, "pending")
                if (!directory.mkdir()) throw IOException("Cannot create package preparation directory")
                var failure: Throwable? = null
                try { return block(directory) }
                catch (error: Throwable) { failure = error; throw error }
                finally {
                    try { if (!directory.deleteRecursively()) throw IOException("Cannot clean package preparation directory") }
                    catch (cleanup: Exception) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
                }
            }
        }
    }

    fun extract(archive: File, workDir: File, checkCancelled: () -> Unit,
                onManifest: (InputStream) -> Unit = {}, onSkipped: (String) -> Unit = {},
                onProgress: (Long, Long) -> Unit = { _, _ -> }, availableSpace: (File) -> Long): List<Entry> {
        val created = mutableListOf<File>()
        try {
            return ZipFile(archive).use { zip ->
                val plan = mutableListOf<Planned>()
                val manifests = mutableListOf<ZipEntry>()
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    checkCancelled()
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    val lower = name.lowercase(Locale.ROOT)
                    if (name.startsWith('/') || name.split('/').any { it == ".." } || name.contains('\u0000')) {
                        onSkipped(name)
                    } else if (lower == "manifest.json" || lower.endsWith("/manifest.json")) {
                        manifests.add(entry)
                    } else if (lower.endsWith(".apk") || lower.endsWith(".obb")) {
                        if (entry.size < 0L || entry.crc < 0L) throw IOException("Archive entry has no size/CRC: $name")
                        total = try { Math.addExact(total, entry.size) }
                            catch (error: ArithmeticException) { throw IOException("Archive size overflows a 64-bit counter", error) }
                        plan.add(Planned(entry, name, lower.endsWith(".obb")))
                    }
                }
                checkCancelled()
                LocalFileCopy.requireSpace(workDir, total, availableSpace)
                onProgress(0, total)
                manifests.forEach { entry ->
                    checkCancelled(); zip.getInputStream(entry).use(onManifest); checkCancelled()
                }
                var apkIndex = 0
                var obbIndex = 0
                var copied = 0L
                val result = plan.map { item ->
                    checkCancelled()
                    LocalFileCopy.requireSpace(workDir, total - copied, availableSpace)
                    val index = if (item.obb) obbIndex++ else apkIndex++
                    val base = item.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val prefix = if (item.obb) "obb-" else ""
                    val output = File(workDir, "$prefix${index.toString().padStart(3, '0')}-$base")
                    if (!output.createNewFile()) throw IOException("Extraction destination already exists: ${output.name}")
                    created.add(output)
                    zip.getInputStream(item.entry).use { input ->
                        output.outputStream().use { out ->
                            LocalFileCopy.copy(input, out, workDir, item.entry.size, checkCancelled,
                                { bytes -> onProgress(copied + bytes, total) }, availableSpace, item.entry.crc)
                        }
                    }
                    copied += item.entry.size
                    Entry(output, item.name, item.obb)
                }
                checkCancelled()
                result
            }
        } catch (error: Throwable) {
            created.forEach { file ->
                try { java.nio.file.Files.deleteIfExists(file.toPath()) }
                catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }
}
