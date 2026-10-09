package lifeos.md.tapilot.update

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private val release = AppRelease("0.1.1", 2, AppUpdate.DOWNLOAD_PAGE)

    @Test fun networkFailureUsesOfficialBackup() {
        val urls = mutableListOf<String>()
        val actual = AppUpdate.check { url, _ ->
            urls.add(url)
            if (url == AppUpdate.MANIFEST_URL) throw IOException("Connection closed")
            release
        }
        assertEquals(release, actual)
        assertEquals(listOf(AppUpdate.MANIFEST_URL, AppUpdate.BACKUP_MANIFEST_URL), urls)
    }

    @Test fun invalidManifestDoesNotTriggerBackup() {
        var attempts = 0
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdate.check { _, _ -> attempts++; throw IllegalArgumentException("Invalid manifest") }
        }
        assertEquals(1, attempts)
    }

    @Test fun bothNetworkFailuresRemainAnError() {
        val primary = IOException("Primary unavailable")
        val backup = IOException("Backup unavailable")
        val error = assertThrows(IOException::class.java) {
            AppUpdate.check { url, _ -> throw if (url == AppUpdate.MANIFEST_URL) primary else backup }
        }
        assertSame(backup, error)
        assertArrayEquals(arrayOf(primary), error.suppressed)
    }
}
