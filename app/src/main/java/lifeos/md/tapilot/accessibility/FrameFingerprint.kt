package lifeos.md.tapilot.accessibility

import android.graphics.Bitmap
import java.util.Base64

/** A small stability hint from the same captured bitmap, never model evidence. */
internal object FrameFingerprint {
    const val ALGORITHM = "rgb-luma-64x96-v1"
    const val WIDTH = 64
    const val HEIGHT = 96

    fun capture(bitmap: Bitmap): String {
        val thumbnail = Bitmap.createScaledBitmap(bitmap, WIDTH, HEIGHT, true)
        return try {
            val pixels = IntArray(WIDTH * HEIGHT)
            thumbnail.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            encode(pixels)
        } finally {
            if (thumbnail !== bitmap) thumbnail.recycle()
        }
    }

    internal fun encode(pixels: IntArray): String {
        require(pixels.size == WIDTH * HEIGHT) { "Unexpected fingerprint dimensions" }
        val luma = ByteArray(pixels.size) { index ->
            val rgb = pixels[index]
            val red = (rgb ushr 16) and 255
            val green = (rgb ushr 8) and 255
            val blue = rgb and 255
            ((2126 * red + 7152 * green + 722 * blue + 5000) / 10000).toByte()
        }
        return Base64.getEncoder().encodeToString(luma)
    }
}
