package lifeos.md.tapilot.report

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRepositoryTest {
    @Test
    fun `missing and pruned reports expose the exact unavailable id`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            val repository = ReportRepository(directory)
            val ids = (1..2).map { index ->
                "$index-00000000-0000-0000-0000-000000000000".also { id ->
                    directory.resolve("$id.html").apply { writeText("report"); setLastModified(index.toLong()) }
                }
            }
            repository.pruneCompletedReports(maxCount = 1)
            val failure = runCatching { repository.requireReport(ids[0]) }.exceptionOrNull()
            assertTrue(failure is MissingReportException)
            assertEquals(ids[0], (failure as MissingReportException).reportId)
            assertEquals("report", repository.requireReport(ids[1]).readText())
            repository.delete(ids[1])
            assertTrue(runCatching { repository.requireReport(ids[1]) }.exceptionOrNull() is MissingReportException)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun `writes report chunks and atomically publishes html`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            val repository = ReportRepository(directory)
            val reportId = repository.begin()

            repository.append(reportId, "<html>")
            repository.append(reportId, "报告内容</html>")
            val report = repository.finish(reportId)

            assertTrue(report.isFile)
            assertEquals("<html>报告内容</html>", report.readText())
            assertFalse(directory.resolve("$reportId.partial").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects report ids that escape the private directory`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            ReportRepository(directory).reportFile("../outside")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `abort closes and removes an incomplete report`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            val repository = ReportRepository(directory)
            val reportId = repository.begin()
            repository.append(reportId, "partial")

            repository.abort(reportId)

            assertFalse(directory.resolve("$reportId.partial").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `imports an existing Node report into private storage`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        val source = Files.createTempFile("midscene-node-report", ".html").toFile()
        try {
            source.writeText("<html>Node 报告</html>")
            val repository = ReportRepository(directory)

            val reportId = repository.importFile(source)

            assertEquals("<html>Node 报告</html>", repository.reportFile(reportId).readText())
            assertFalse(directory.resolve("$reportId.partial").exists())
        } finally {
            directory.deleteRecursively()
            source.delete()
        }
    }

    @Test
    fun `deletes a completed report`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            val repository = ReportRepository(directory)
            val reportId = repository.begin()
            repository.append(reportId, "<html></html>")
            repository.finish(reportId)

            repository.delete(reportId)

            assertFalse(repository.reportFile(reportId).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `prunes old reports while always retaining the newest`() {
        val directory = Files.createTempDirectory("midscene-reports").toFile()
        try {
            val reports = (1..3).map { index ->
                directory.resolve("$index-00000000-0000-0000-0000-000000000000.html").apply {
                    writeText("x".repeat(10))
                    setLastModified(index.toLong())
                }
            }
            val repository = ReportRepository(directory)

            repository.pruneCompletedReports(maxCount = 2, maxTotalBytes = 15)

            assertTrue(reports[2].exists())
            assertFalse(reports[1].exists())
            assertFalse(reports[0].exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
