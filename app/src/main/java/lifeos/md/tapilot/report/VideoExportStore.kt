package lifeos.md.tapilot.report

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import java.io.OutputStream

/** Only one bounded, sequential transfer is active; incomplete rows are never published. */
internal class VideoExportStore(private val context: Context) : AutoCloseable {
    private var uri: Uri? = null
    private var output: OutputStream? = null
    private var expectedBytes = 0L
    private var writtenBytes = 0L

    fun begin(size: Long, mime: String) {
        check(uri == null) { "Another video is being saved" }
        require(size in 1..MAX_BYTES) { "Video must be smaller than 256 MB" }
        require(mime == "video/webm" || mime == "video/mp4") { "Unsupported video type" }
        val extension = if (mime == "video/mp4") "mp4" else "webm"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "tapilot-replay-${System.currentTimeMillis()}.$extension")
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Tapilot")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create video")
        try {
            output = context.contentResolver.openOutputStream(checkNotNull(uri))
                ?: error("Unable to open video")
            expectedBytes = size
            writtenBytes = 0
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun append(encoded: String) {
        require(encoded.length <= 90_000) { "Video chunk is too large" }
        val stream = checkNotNull(output) { "No video transfer" }
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        check(writtenBytes + bytes.size <= expectedBytes) { "Unexpected video size" }
        stream.write(bytes)
        writtenBytes += bytes.size
    }

    fun finish() {
        check(writtenBytes == expectedBytes && writtenBytes > 0) { "Incomplete video" }
        checkNotNull(output).close()
        output = null
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        check(context.contentResolver.update(checkNotNull(uri), values, null, null) == 1) {
            "Unable to publish video"
        }
        uri = null
    }

    override fun close() {
        try {
            output?.close()
        } finally {
            output = null
            uri?.let { context.contentResolver.delete(it, null, null) }
            uri = null
        }
    }

    companion object {
        private const val MAX_BYTES = 256L * 1024 * 1024
    }
}
