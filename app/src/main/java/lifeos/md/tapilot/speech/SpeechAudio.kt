package lifeos.md.tapilot.speech

import java.io.ByteArrayOutputStream
import kotlin.math.sqrt
import kotlin.math.log10

/** Bounded PCM retained only in memory for retrying a failed request. */
internal class SpeechAudio(private val limit: Int = 16000 * 2 * 60) {
    private val bytes = ByteArrayOutputStream()
    fun append(pcm: ByteArray) {
        require(bytes.size() + pcm.size <= limit) { "Recording exceeds 60 seconds" }
        bytes.write(pcm)
    }
    fun snapshot(): ByteArray = bytes.toByteArray()
    companion object {
        fun level(pcm: ByteArray): Float {
            if (pcm.size < 2) return 0f
            var energy = 0.0
            for (i in 0 until pcm.size - 1 step 2) {
                val sample = ((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort().toDouble()
                energy += sample * sample
            }
            val rms = sqrt(energy / (pcm.size / 2)) / 32768.0
            return ((20 * log10(maxOf(rms, 0.000001)) + 60) / 45).toFloat().coerceIn(0f, 1f)
        }
    }
}
