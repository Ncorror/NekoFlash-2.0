package ru.forum.adbfastboottool

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Publish a completed sibling file without deleting the previous result first. */
internal object FilePublication {
    fun temporarySibling(target: File): File =
        Files.createTempFile(target.absoluteFile.parentFile.toPath(), ".nekoflash-", ".part").toFile()

    fun replace(completed: File, target: File) {
        require(completed.canonicalFile.parentFile == target.absoluteFile.parentFile.canonicalFile)
        // Unsupported atomic replacement fails without deleting the previous good file.
        Files.move(completed.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    fun publishNew(completed: File, target: File) {
        // ATOMIC_MOVE has provider-specific existing-target behavior. Imports
        // instead use no-replace move and retry a distinct name on collision.
        Files.move(completed.toPath(), target.toPath())
    }
}
