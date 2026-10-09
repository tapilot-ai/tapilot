package lifeos.md.tapilot.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun manifest() = JSONObject().put("schemaVersion", 1).put("applicationId", "lifeos.md.tapilot")
        .put("version", "0.1.1").put("versionCode", 2).put("downloadUrl", AppUpdate.DOWNLOAD_PAGE)

    @Test fun newerSameAndOlderVersions() {
        val release = AppRelease.parse(manifest().toString())
        assertTrue(release.isNewerThan(1))
        assertFalse(release.isNewerThan(2))
        assertFalse(release.isNewerThan(3))
        assertEquals(AppUpdate.DOWNLOAD_PAGE, release.downloadUrl)
    }

    @Test fun usesVersionCodeInsteadOfVersionName() {
        assertTrue(AppRelease.parse(manifest().put("version", "0.0.1").toString()).isNewerThan(1))
        assertFalse(AppRelease.parse(manifest().put("version", "99.0.0").toString()).isNewerThan(2))
    }

    @Test fun rejectsWrongAppAndMalformedVersion() {
        listOf(manifest().put("applicationId", "another.app"), manifest().put("schemaVersion", 2),
            manifest().put("versionCode", 2.5), manifest().put("versionCode", -1), manifest().put("version", " ")).forEach {
            assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(it.toString()) }
        }
    }

    @Test fun rejectsUntrustedDownloadDestinations() {
        listOf("http://my.feishu.cn/docx/Test", "https://feishu.cn.evil.test/docx/Test",
            "https://user@my.feishu.cn/docx/Test", "https://my.feishu.cn:8080/docx/Test", "https://my.feishu.cn/login").forEach {
            assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(manifest().put("downloadUrl", it).toString()) }
        }
    }
}
