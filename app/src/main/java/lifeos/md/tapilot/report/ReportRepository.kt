package lifeos.md.tapilot.report

import java.io.BufferedWriter
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class MissingReportException(val reportId: String) : IllegalStateException("Report is no longer available: $reportId")

class ReportRepository(private val directory: File) : AutoCloseable {
    private val writers = mutableMapOf<String, BufferedWriter>()

    init {
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create report directory: ${directory.absolutePath}"
        }
    }

    fun cleanupStaleDrafts(olderThanMillis: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        directory.listFiles { file ->
            file.extension == "partial" && file.lastModified() < cutoff
        }?.forEach { staleDraft ->
            check(staleDraft.delete()) { "Unable to delete stale report draft: ${staleDraft.name}" }
        }
    }

    @Synchronized
    fun begin(): String {
        pruneCompletedReports()
        val reportId = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val partial = partialFile(reportId)
        check(partial.createNewFile()) { "Unable to create report draft" }
        writers[reportId] = partial.bufferedWriter(Charsets.UTF_8)
        return reportId
    }

    @Synchronized
    fun append(reportId: String, chunk: String) {
        val writer = writers[reportId]
            ?: throw IllegalStateException("Unknown or completed report: $reportId")
        writer.write(chunk)
    }

    @Synchronized
    fun finish(reportId: String): File {
        val writer = writers.remove(reportId)
            ?: throw IllegalStateException("Unknown or completed report: $reportId")
        writer.use { it.flush() }
        return publish(reportId)
    }

    @Synchronized
    fun importFile(source: File): String {
        require(source.isFile) { "Report does not exist: ${source.absolutePath}" }
        pruneCompletedReports()
        val reportId = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
        source.inputStream().use { input ->
            partialFile(reportId).outputStream().use { output -> input.copyTo(output) }
        }
        publish(reportId)
        return reportId
    }

    private fun publish(reportId: String): File {
        val partial = partialFile(reportId)
        val report = reportFile(reportId)
        try {
            Files.move(
                partial.toPath(),
                report.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                partial.toPath(),
                report.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        pruneCompletedReports()
        return report
    }

    @Synchronized
    fun abort(reportId: String) {
        require(SAFE_REPORT_ID.matches(reportId)) { "Invalid report id" }
        writers.remove(reportId)?.let { writer -> runCatching { writer.close() } }
        partialFile(reportId).delete()
    }

    @Synchronized
    fun pruneCompletedReports(
        maxCount: Int = MAX_REPORT_COUNT,
        maxTotalBytes: Long = MAX_REPORT_BYTES,
    ) {
        require(maxCount >= 1) { "At least one completed report must be retained" }
        require(maxTotalBytes >= 1) { "Report byte limit must be positive" }
        val reports = directory.listFiles { file -> file.extension == "html" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        var retainedCount = 0
        var retainedBytes = 0L
        reports.forEachIndexed { index, report ->
            val keepNewest = index == 0
            val withinLimits = retainedCount < maxCount &&
                retainedBytes + report.length() <= maxTotalBytes
            if (keepNewest || withinLimits) {
                retainedCount += 1
                retainedBytes += report.length()
            } else {
                check(report.delete()) { "Unable to delete expired report: ${report.name}" }
            }
        }
    }

    @Synchronized
    override fun close() {
        writers.keys.toList().forEach(::abort)
    }

    fun reportFile(reportId: String): File {
        require(SAFE_REPORT_ID.matches(reportId)) { "Invalid report id" }
        return File(directory, "$reportId.html")
    }

    fun requireReport(reportId: String): File = reportFile(reportId).also {
        if (!it.isFile) throw MissingReportException(reportId)
    }

    @Synchronized
    fun delete(reportId: String) {
        val report = reportFile(reportId)
        check(!report.exists() || report.delete()) { "Unable to delete report: $reportId" }
    }

    private fun partialFile(reportId: String): File {
        require(SAFE_REPORT_ID.matches(reportId)) { "Invalid report id" }
        return File(directory, "$reportId.partial")
    }

    companion object {
        private val SAFE_REPORT_ID = Regex("[0-9]+-[0-9a-f-]+")
        const val MAX_REPORT_COUNT = 20
        const val MAX_REPORT_BYTES = 200L * 1024 * 1024
    }
}
