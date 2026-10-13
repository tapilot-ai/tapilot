package lifeos.md.tapilot.chat

import org.json.JSONArray
import org.json.JSONObject

internal data class ReplyPart(val text: String, val formattedJson: String? = null, val operationCount: Int? = null)

/** Split structured data from prose without changing the stored reply or copy action. */
internal fun replyParts(text: String): List<ReplyPart> {
    val parts = mutableListOf<ReplyPart>()
    var plainStart = 0
    var index = 0
    while (index < text.length) {
        val start = index
        val opening = text[index++]
        if (opening != '[' && opening != '{') continue
        var tokenStart = index
        while (tokenStart < text.length && text[tokenStart].isWhitespace()) tokenStart++
        val next = text.getOrNull(tokenStart) ?: break
        if (opening == '{' && next !in "\"}" || opening == '[' && next !in "\"{[]-0123456789tfn") continue
        if (next in "tfn" && !listOf("true", "false", "null").any { text.startsWith(it, tokenStart) }) continue
        val stack = ArrayDeque<Char>().apply { addLast(opening) }
        var quoted = false
        var escaped = false
        while (index < text.length && stack.isNotEmpty()) {
            val char = text[index++]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '[', '{' -> stack.addLast(char)
                ']', '}' -> {
                    if (stack.last() != if (char == ']') '[' else '{') break
                    stack.removeLast()
                }
            }
        }
        if (stack.isNotEmpty()) break // An incomplete payload remains ordinary text.
        val raw = text.substring(start, index)
        val part = runCatching {
            if (opening == '{') ReplyPart(raw, JSONObject(raw).toString(2)) else {
                val array = JSONArray(raw)
                val operations = array.length() > 0 && (0 until array.length()).all {
                    array.optJSONObject(it)?.let { item -> item.has("instruction") && item.has("success") && item.has("result") } == true
                }
                ReplyPart(raw, array.toString(2), array.length().takeIf { operations })
            }
        }.getOrNull() ?: continue
        var prefix = text.substring(plainStart, start)
        val fence = Regex("```(?:json)?\\s*$", RegexOption.IGNORE_CASE).find(prefix)
        if (fence != null) {
            prefix = prefix.substring(0, fence.range.first)
            Regex("^\\s*```").find(text.substring(index))?.let { index += it.value.length }
        }
        if (prefix.isNotBlank()) parts += ReplyPart(prefix)
        parts += part
        plainStart = index
    }
    if (plainStart < text.length) parts += ReplyPart(text.substring(plainStart))
    return parts
}
