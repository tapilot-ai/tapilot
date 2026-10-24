package lifeos.md.tapilot.chat

/** This attempt's wall time, including preparation and recovery, not just model time. */
internal fun taskDurationLabel(millis: Long, zh: Boolean): String {
    require(millis >= 0)
    if (millis < 1_000) return if (zh) "耗时不足 1 秒" else "Duration <1s"
    val seconds = millis / 1_000 + if (millis % 1_000 >= 500) 1 else 0
    val units = listOf(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    val suffixes = if (zh) listOf("小时", "分", "秒") else listOf("h", "m", "s")
    val value = units.mapIndexedNotNull { index, count ->
        if (count == 0L) null else if (zh) "$count ${suffixes[index]}" else "$count${suffixes[index]}"
    }.joinToString(" ")
    return if (zh) "耗时 $value" else "Duration $value"
}
