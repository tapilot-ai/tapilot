package lifeos.md.tapilot.report

import android.content.Context

object DeletedReportStore {
    private const val PREFERENCES = "deleted-reports"
    private const val REPORT_IDS = "report-ids"

    @Synchronized
    fun markDeleted(context: Context, reportId: String) {
        val preferences = preferences(context)
        val updated = preferences.getStringSet(REPORT_IDS, emptySet()).orEmpty() + reportId
        check(preferences.edit().putStringSet(REPORT_IDS, updated).commit()) {
            "Unable to remember deleted report: $reportId"
        }
    }

    @Synchronized
    fun consume(context: Context): Set<String> {
        val preferences = preferences(context)
        val reportIds = preferences.getStringSet(REPORT_IDS, emptySet()).orEmpty().toSet()
        if (reportIds.isNotEmpty()) {
            check(preferences.edit().remove(REPORT_IDS).commit()) {
                "Unable to clear deleted report notifications"
            }
        }
        return reportIds
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
