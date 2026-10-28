package lifeos.md.tapilot.chatgpt

/** Choose only models the account actually exposes; never replace an explicit valid selection. */
object ChatGptModelSelection {
    private val luna = Regex("^gpt-(\\d+)(?:\\.(\\d+))?-luna(?:-(\\d{4}-\\d{2}-\\d{2}))?$")
    fun select(available: List<String>, selected: String = ""): String {
        require(available.isNotEmpty()) { "No available ChatGPT models" }
        if (selected in available) return selected
        if ("gpt-6.1-terra" in available) return "gpt-6.1-terra"
        return available.mapNotNull { id -> luna.matchEntire(id)?.let { id to it.groupValues } }
            .maxWithOrNull(compareBy<Pair<String, List<String>>> { it.second[1].toInt() }
                .thenBy { it.second[2].toIntOrNull() ?: 0 }
                // The rolling alias is preferred to a pinned snapshot of the same generation.
                .thenBy { it.second[3].ifEmpty { "9999-99-99" } })?.first ?: available.first()
    }
}
