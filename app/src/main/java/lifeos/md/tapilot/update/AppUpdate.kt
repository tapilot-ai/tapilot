package lifeos.md.tapilot.update

import java.net.URI
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppRelease(val version: String, val versionCode: Long, val downloadUrl: String) {
    fun isNewerThan(installedCode: Long) = versionCode > installedCode

    companion object {
        fun parse(json: String): AppRelease {
            val value = JSONObject(json)
            require(value.optInt("schemaVersion") == 1 && value.optString("applicationId") == "lifeos.md.tapilot")
            val version = value.getString("version").trim()
            val code = value.get("versionCode").toString().toLongOrNull()
            require(version.isNotEmpty() && code != null && code > 0)
            val url = value.getString("downloadUrl")
            val uri = URI(url)
            require(uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443))
            require(uri.host == "feishu.cn" || uri.host?.endsWith(".feishu.cn") == true)
            require(Regex("/(docx|wiki)/[a-zA-Z0-9]+/?").matches(uri.path.orEmpty()))
            return AppRelease(version, code, url)
        }
    }
}

object AppUpdate {
    const val MANIFEST_URL = "https://tapilot.lifeos.md/releases/android/latest.json"
    const val BACKUP_MANIFEST_URL = "https://tapilot-site.pages.dev/releases/android/latest.json"
    const val DOWNLOAD_PAGE = "https://my.feishu.cn/docx/TiXgdjtJ5oYSEZxIVzncWI0Kn4g"
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    fun check(): AppRelease = check { url, timeoutSeconds ->
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        call.execute().use { response ->
            check(response.isSuccessful) { "Update service HTTP ${response.code}" }
            val source = checkNotNull(response.body).source()
            source.request(65_537)
            require(source.buffer.size <= 65_536) { "Update manifest too large" }
            AppRelease.parse(source.readUtf8())
        }
    }

    internal fun check(fetch: (String, Long) -> AppRelease): AppRelease {
        return try {
            fetch(MANIFEST_URL, 7)
        } catch (primary: IOException) {
            if (Thread.currentThread().isInterrupted) throw primary
            try { fetch(BACKUP_MANIFEST_URL, 8) }
            catch (backup: Exception) { backup.addSuppressed(primary); throw backup }
        }
    }
}
