package lifeos.md.tapilot.report

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import lifeos.md.tapilot.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Owns report export I/O and its lifecycle, independently of viewer layout/theme/readiness. */
internal class ReportExports(private val activity: Activity, private val report: File) : AutoCloseable {
    private val fileWorker = Executors.newSingleThreadExecutor()
    private val videoExport = VideoExportStore(activity.applicationContext)
    @Volatile private var closed = false
    private val contentResolver get() = activity.contentResolver
    private fun getString(id: Int, vararg args: Any?) = activity.getString(id, *args)
    private fun startActivity(intent: Intent) = activity.startActivity(intent)
    private fun runOnUiThread(action: () -> Unit) = activity.runOnUiThread {
        if (!closed && !activity.isDestroyed) action()
    }
    private fun submit(action: () -> Unit) { if (!closed) fileWorker.execute(action) }

    private fun reportUri() = FileProvider.getUriForFile(activity, "${activity.packageName}.reports", report)

    fun shareReport() {
        val uri = reportUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, report.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.report_share)))
    }

    fun saveToUri(uri: Uri) {
        submit {
            val saved = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    report.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open destination")
            }
            runOnUiThread {
                if (!activity.isDestroyed) Toast.makeText(
                    activity,
                    if (saved.isSuccess) getString(R.string.report_saved, report.name)
                    else getString(R.string.report_save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun installVideoExport(view: WebView, host: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            view.setDownloadListener { _, _, _, _, _ ->
                Toast.makeText(activity, getString(R.string.video_save_failed, "Update Android System WebView"), Toast.LENGTH_LONG).show()
            }
            return
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.addWebMessageListener(
            view, "TapilotVideo", setOf("https://$host"),
        ) { _, message, _, isMainFrame, reply ->
            if (!isMainFrame || activity.isDestroyed) return@addWebMessageListener
            val payload = message.data ?: return@addWebMessageListener
            if (payload.length > 100_000) return@addWebMessageListener
            submit {
                var id = 0
                var completed = false
                var failure: String? = null
                val result = runCatching {
                    val request = JSONObject(payload)
                    id = request.getInt("id")
                    when (request.getString("method")) {
                        "begin" -> videoExport.begin(request.getLong("size"), request.getString("mime"))
                        "append" -> videoExport.append(request.getString("data"))
                        "finish" -> { videoExport.finish(); completed = true }
                        "abort" -> {
                            videoExport.close()
                            failure = request.optString("error", "Video export cancelled")
                        }
                        else -> error("Unknown video export operation")
                    }
                }
                if (result.isFailure) {
                    failure = result.exceptionOrNull()?.message ?: "Video export failed"
                    runCatching { videoExport.close() }
                }
                val response = JSONObject().put("id", id)
                if (result.isFailure) response.put("error", failure)
                val notice = failure
                runOnUiThread {
                    if (!activity.isDestroyed) {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            reply.postMessage(response.toString())
                        }
                        if (completed || notice != null) Toast.makeText(
                            activity,
                            if (completed) getString(R.string.video_saved)
                            else getString(R.string.video_save_failed, notice),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    fun openExternally() {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(reportUri(), "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(view, getString(R.string.report_open_external)))
    }

    fun saveToDownloads() = submit {
        val saved = runCatching {
            val fileName = "midscene-report-${System.currentTimeMillis()}.html"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/html")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create Downloads report")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    report.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open Downloads report")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                check(contentResolver.update(uri, values, null, null) == 1) { "Unable to publish Downloads report" }
            } catch (error: Throwable) {
                contentResolver.delete(uri, null, null)
                throw error
            }
            fileName
        }
        runOnUiThread {
            Toast.makeText(activity, saved.fold(
                onSuccess = { getString(R.string.report_saved, it) },
                onFailure = { getString(R.string.report_save_failed) },
            ), Toast.LENGTH_LONG).show()
        }
    }


    override fun close() {
        if (closed) return
        closed = true
        fileWorker.execute { runCatching { videoExport.close() } }
        fileWorker.shutdown()
    }
}
