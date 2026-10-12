package lifeos.md.tapilot.voice

/** Network chunks may end between the two bytes of a linear16 sample. */
internal class PcmStream {
    private var pending: Byte? = null
    fun append(chunk: ByteArray): ByteArray {
        val data = pending?.let { byteArrayOf(it) + chunk } ?: chunk
        val size = data.size / 2 * 2
        pending = if (size != data.size) data.last() else null
        return if (size == data.size) data else data.copyOf(size)
    }
    fun finish() { check(pending == null) { "Incomplete PCM audio response" } }
}
