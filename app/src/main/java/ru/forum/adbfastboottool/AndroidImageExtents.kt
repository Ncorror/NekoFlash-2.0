package ru.forum.adbfastboottool

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.RandomAccessFile

/** SEEK_DATA avoids scanning the unallocated part of a large local mkfs image. */
internal object AndroidImageExtents {
    fun nextData(file: RandomAccessFile, offset: Long): Long? = try {
        Os.lseek(file.fd, offset, 3) // Linux SEEK_DATA; available independently of Java constants.
    } catch (error: ErrnoException) {
        when (error.errno) {
            OsConstants.ENXIO -> null
            OsConstants.EINVAL, OsConstants.EOPNOTSUPP -> offset // Filesystem has no extent query; stream instead.
            else -> throw error
        }
    }
}
