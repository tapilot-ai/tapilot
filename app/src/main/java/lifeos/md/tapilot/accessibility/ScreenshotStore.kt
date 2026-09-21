package lifeos.md.tapilot.accessibility

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import java.util.Base64

internal object ScreenshotStore {
    fun save(resolver: ContentResolver, dataUrl: String, cancelled: () -> Boolean) {
        require(dataUrl.startsWith("data:image/png;base64,")) { "Expected PNG screenshot" }
        if (cancelled()) throw MidsceneAccessibilityService.SessionCancelledException()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Tapilot-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tapilot")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create screenshot in Pictures/Tapilot")
        try {
            resolver.openOutputStream(uri)?.use {
                it.write(Base64.getDecoder().decode(dataUrl.substringAfter(',')))
            } ?: error("Unable to write screenshot")
            if (cancelled()) throw MidsceneAccessibilityService.SessionCancelledException()
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null) == 1) { "Unable to publish screenshot" }
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
