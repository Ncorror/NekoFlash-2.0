package ru.forum.adbfastboottool

import android.annotation.SuppressLint
import java.io.File

/**
 * Conservative check for temporary Fastboot sparse packets.
 *
 * We deliberately require *currently usable* bytes before the first device
 * mutation. StorageManager.getAllocatableBytes() includes cache that Android
 * might evict later; it is not a guaranteed allocation and can change device
 * state. Do not silently replace this preflight with optimistic allocation.
 */
@SuppressLint("UsableSpace")
internal fun hasImmediateSparseSpace(directory: File, requiredBytes: Long): Boolean =
    directory.usableSpace >= requiredBytes
