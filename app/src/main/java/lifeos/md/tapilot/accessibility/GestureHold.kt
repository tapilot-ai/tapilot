package lifeos.md.tapilot.accessibility

/** Android may acknowledge a stationary continued stroke as soon as DOWN is sent. */
internal class GestureHold(
    durationMs: Long,
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> Unit,
) {
    private val deadline = now() + durationMs

    fun continueWhenReady(continuation: () -> Unit) {
        schedule((deadline - now()).coerceAtLeast(0), continuation)
    }
}
