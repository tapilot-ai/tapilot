package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.AppText.text as appText
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Converts selected local content into formats the conversation model can actually read. */
object AttachmentReader {
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_DOCUMENT_BYTES = 12 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 256 * 1024

    fun read(context: Context, uri: Uri): JSONObject {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.take(120) ?: "attachment"
        val mime = resolver.getType(uri).orEmpty()
        val isImage = mime.startsWith("image/") || name.endsWith(".jpg", true) ||
            name.endsWith(".jpeg", true) || name.endsWith(".png", true) ||
            name.endsWith(".webp", true) || name.endsWith(".heic", true)
        val isPdf = mime == "application/pdf" || name.endsWith(".pdf", true)
        val maxBytes = if (isImage) MAX_IMAGE_BYTES else MAX_DOCUMENT_BYTES
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { appText("附件超过 12 MB，请选择更小的文件", "Attachment exceeds 12 MB. Choose a smaller file") }
                output.write(chunk, 0, count)
            }
            output.toByteArray()
        } ?: error(appText("无法读取附件", "Unable to read attachment"))
        require(bytes.isNotEmpty()) { appText("附件为空", "Attachment is empty") }
        return when {
            isImage -> JSONObject().put("name", name).put("kind", "image")
                .put("images", JSONArray().put(imageDataUrl(bytes)))
            isPdf -> {
                val (pages, pageCount) = renderPdf(context, bytes)
                JSONObject().put("name", name).put("kind", "pdf")
                    .put("images", pages).put("pageCount", pageCount)
            }
            isText(mime, name) -> {
                require(bytes.size <= MAX_TEXT_BYTES) { appText("文本文件超过 256 KB，请选择更小的文件", "Text file exceeds 256 KB. Choose a smaller file") }
                JSONObject().put("name", name).put("kind", "text")
                    .put("text", String(bytes, Charsets.UTF_8))
                    .put("images", JSONArray())
            }
            else -> throw UnsupportedOperationException(appText("目前可发送图片、PDF 和文本文件", "Supported attachments: images, PDF and text files"))
        }
    }

    private fun isText(mime: String, name: String): Boolean =
        mime.startsWith("text/") || mime in setOf(
            "application/json", "application/xml", "application/javascript",
        ) || listOf(".txt", ".md", ".csv", ".json", ".xml", ".log", ".yaml", ".yml")
            .any { name.endsWith(it, true) }

    private fun imageDataUrl(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { appText("无法解码图片", "Unable to decode image") }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error(appText("无法解码图片", "Unable to decode image"))
        return bitmap.useJpegDataUrl()
    }

    private fun renderPdf(context: Context, bytes: ByteArray): Pair<JSONArray, Int> {
        val file = File.createTempFile("attachment-", ".pdf", context.cacheDir)
        try {
            FileOutputStream(file).use { it.write(bytes) }
            val pages = JSONArray()
            var pageCount = 0
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                require(pdf.pageCount > 0) { appText("PDF 没有页面", "PDF has no pages") }
                pageCount = pdf.pageCount
                for (index in 0 until minOf(pdf.pageCount, 4)) {
                    pdf.openPage(index).use { page ->
                        val scale = minOf(1200f / page.width, 1600f / page.height)
                        val bitmap = Bitmap.createBitmap(
                            maxOf(1, (page.width * scale).toInt()),
                            maxOf(1, (page.height * scale).toInt()),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.put(bitmap.useJpegDataUrl())
                    }
                }
            }
            return pages to pageCount
        } finally {
            file.delete()
        }
    }

    private fun Bitmap.useJpegDataUrl(): String = try {
        val output = ByteArrayOutputStream()
        check(compress(Bitmap.CompressFormat.JPEG, 82, output)) { appText("图片转换失败", "Image conversion failed") }
        "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
    } finally {
        recycle()
    }
}
