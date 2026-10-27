package ru.forum.adbfastboottool

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Installed only with the instrumentation APK; isolates real Binder/FD behavior from USB. */
class FirmwareFixtureProvider : ContentProvider() {
    private val image get() = File(requireNotNull(context).cacheDir, "fixture.img")
    @Volatile private var deny = false
    override fun onCreate(): Boolean { reset(8192); return true }
    private fun reset(size: Int) { image.writeBytes(ByteArray(size) { (it % 251).toByte() }) }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        when (method) {
            "reset" -> { deny = false; reset(8192) }
            "deny" -> deny = true
            "resize" -> reset(requireNotNull(arg).toInt())
            else -> error("Unknown fixture command")
        }
        return Bundle()
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(mode == "r")
        if (deny) throw SecurityException("Fixture provider revoked read access")
        if (uri.lastPathSegment == "pipe") {
            val pipe = ParcelFileDescriptor.createPipe()
            pipe[1].close()
            return pipe[0]
        }
        return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) "provider image.img" else 1L })
        }
    }
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
}
