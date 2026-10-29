package ru.forum.adbfastboottool

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/** Same-filesystem staging; the app-private OS lock also protects restart cleanup. */
internal object WorkspaceFileImporter {
    fun importFile(workspace: File, owner: String, name: String, expectedSize: Long?,
                   cancellation: ImportCancellation, openInput: () -> InputStream,
                   onProgress: (Long) -> Unit = {}, availableSpace: (File) -> Long,
                   privateLockFile: File? = null): File {
        require(owner.matches(Regex("[A-Za-z0-9._-]+")))
        cancellation.check()
        if (!workspace.isDirectory) throw IOException("Workspace is unavailable")
        val staging = File(workspace, ".nekoflash-import-$owner")
        if (!staging.exists() && !staging.mkdir()) throw IOException("Cannot create import staging directory")
        if (!staging.isDirectory || staging.canonicalFile != File(workspace.canonicalFile, staging.name)) {
            throw IOException("Invalid import staging directory")
        }
        val lockPath = (privateLockFile ?: File(staging, "lock")).toPath()
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                ?: throw IOException("Another workspace import is still running")
            lock.use {
                val staged = File(staging, "pending.part")
                // Only this reserved file is ours; never sweep unrelated user .part files.
                Files.deleteIfExists(staged.toPath())
                var failure: Throwable? = null
                try {
                    cancellation.check()
                    expectedSize?.let { LocalFileCopy.requireSpace(workspace, it, availableSpace) }
                    val copied = openInput().use { input ->
                        staged.outputStream().use { output ->
                            LocalFileCopy.copy(input, output, workspace, expectedSize, cancellation::check,
                                onProgress, availableSpace).also { cancellation.check(); output.fd.sync() }
                        }
                    }
                    cancellation.detachInput()
                    cancellation.check()
                    if (copied <= 0L || staged.length() != copied) throw IOException("Imported file is empty or incomplete")
                    val displayName = WorkspaceImportNaming.sanitizeImportedFileName(name)
                    return cancellation.commit {
                        var target = WorkspaceImportNaming.uniqueTargetFile(workspace, displayName)
                        while (true) {
                            try { FilePublication.publishNew(staged, target); break }
                            catch (_: java.nio.file.FileAlreadyExistsException) {
                                target = WorkspaceImportNaming.uniqueTargetFile(workspace, displayName)
                            }
                        }
                        target
                    }
                } catch (error: Throwable) {
                    failure = error
                    throw error
                } finally {
                    cancellation.detachInput()
                    try { Files.deleteIfExists(staged.toPath()) }
                    catch (cleanup: Exception) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
                }
            }
        }
    }
}
