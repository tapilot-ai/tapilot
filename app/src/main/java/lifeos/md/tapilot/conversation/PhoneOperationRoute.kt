package lifeos.md.tapilot.conversation

import org.json.JSONArray

/** Only an unambiguous installed launcher entry can use the direct-launch path. */
object PhoneOperationRoute {
    fun findApps(apps: JSONArray, query: String): JSONArray = JSONArray(apps.objects().filter { app ->
        listOf("label", "packageName", "component").any { app.optString(it).contains(query.trim(), ignoreCase = true) }
    })

    fun resolveApp(apps: JSONArray, target: String): String {
        if (target.isBlank()) return ""
        val matches = apps.objects().filter { app ->
            listOf("label", "packageName", "component").any { app.optString(it).equals(target.trim(), ignoreCase = true) }
        }.map { it.getString("component") }.distinct()
        require(matches.size == 1) { "App target is missing or ambiguous; use find_apps and clarify before launching: $target" }
        return matches.single()
    }
}
