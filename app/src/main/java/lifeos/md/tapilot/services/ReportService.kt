package lifeos.md.tapilot.services

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import androidx.core.content.FileProvider
import lifeos.md.tapilot.ReportActivity
import lifeos.md.tapilot.report.ReportRepository
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor

/** Report storage and export operations; activity navigation is supplied by the host. */
internal class ReportService(
    private val context: () -> Context,
    private val reportRepository: ReportRepository,
    private val executor: Executor,
    private val open: (Intent) -> Unit,
) {
    private val activity get() = context()
    fun call(method: String, params: JSONObject, complete: (Result<Any?>) -> Unit) {
        fun success(value: Any?) = complete(Result.success(value))
        fun execute(operation: () -> Any?) { executor.execute { complete(runCatching(operation)) } }
        when (method) {
            "report.begin" -> execute {
                JSONObject().put("reportId", reportRepository.begin())
            }
            "report.append" -> execute {
                reportRepository.append(
                    params.getString("reportId"),
                    params.getString("chunk"),
                )
                JSONObject.NULL
            }
            "report.finish" -> execute {
                val reportId = params.getString("reportId")
                reportRepository.finish(reportId)
                JSONObject.NULL
            }
            "report.abort" -> execute {
                val reportId = params.getString("reportId")
                reportRepository.abort(reportId)
                JSONObject.NULL
            }
            "report.open" -> {
                val reportId = params.getString("reportId")
                check(reportRepository.reportFile(reportId).isFile) {
                    "Report does not exist: $reportId"
                }
                open(
                    Intent(activity, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_REPORT_ID, reportId),
                )
                success(JSONObject.NULL)
            }
            "report.openExternal" -> {
                val report = requireReport(params)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(reportUri(report), "text/html")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                open(Intent.createChooser(intent, report.name))
                success(JSONObject.NULL)
            }
            "report.share" -> {
                val report = requireReport(params)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_STREAM, reportUri(report))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                open(Intent.createChooser(intent, report.name))
                success(JSONObject.NULL)
            }
            "report.export" -> execute {
                val report = requireReport(params)
                val fileName = "midscene-report-${System.currentTimeMillis()}.html"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create Downloads report")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        report.inputStream().use { it.copyTo(output) }
                    } ?: error("Unable to open Downloads report")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                JSONObject().put("fileName", fileName)
            }
            "report.delete" -> execute {
                reportRepository.delete(params.getString("reportId"))
                JSONObject.NULL
            }
            else -> throw UnsupportedOperationException("Unsupported report method: $method")
        }
    }
    private fun requireReport(params: JSONObject): File {
        val reportId = params.getString("reportId")
        return reportRepository.reportFile(reportId).also {
            check(it.isFile) { "Report does not exist: $reportId" }
        }
    }

    private fun reportUri(report: File) = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.reports",
        report,
    )

}
