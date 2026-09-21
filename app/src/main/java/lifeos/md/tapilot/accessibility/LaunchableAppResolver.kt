package lifeos.md.tapilot.accessibility

import java.util.Locale

internal data class LaunchableApp(val label: String, val packageName: String)

internal object LaunchableAppResolver {
    fun resolve(name: String, apps: List<LaunchableApp>): String? {
        val matches = apps
            .filter { it.label.trim().lowercase(Locale.ROOT) == name.trim().lowercase(Locale.ROOT) }
            .map { it.packageName }
            .distinct()
        require(matches.size <= 1) { "Multiple installed apps are named: $name" }
        return matches.singleOrNull()
    }
}
