package ru.forum.adbfastboottool

import java.io.File

/** PackageManager installs these PIE programs; do not copy executables into writable app data. */
internal object BundledFilesystemTools {
    fun fromDirectory(nativeLibraryDir: File): Map<String, File> = mapOf(
        "ext4" to File(nativeLibraryDir, "libnekoflash_mke2fs.so"),
        "f2fs" to File(nativeLibraryDir, "libnekoflash_mkfs_f2fs.so")
    )
}
