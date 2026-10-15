package lifeos.md.tapilot.wireless

import android.net.Network
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** The physically scanned QR pins the temporary computer receiver's TLS certificate. */
internal object ComputerPairing {
    fun send(qr: String, phoneLink: String, network: Network) {
        require(qr.startsWith("tapilot://computer/") && qr.length <= 4096) { "请扫描 Android Use 电脑配对窗口的二维码" }
        val data = JSONObject(String(Base64.getUrlDecoder().decode(qr.removePrefix("tapilot://computer/"))))
        require(data.getInt("version") == 1 && data.getLong("expiresAt") > System.currentTimeMillis()) { "配对二维码已过期，请在电脑重新打开配对窗口" }
        val uri = URI(data.getString("url"))
        val host = uri.host.orEmpty()
        require(host.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) && InetAddress.getByName(host) is Inet4Address) { "无效的电脑地址" }
        require(uri.scheme == "https" && uri.port in 1024..65535 && uri.path.matches(Regex("/offer/[A-Za-z0-9_-]{43}")) && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null) { "无效的电脑配对地址" }
        val fingerprint = data.getString("fingerprint")
        require(fingerprint.matches(Regex("[a-f0-9]{64}"))) { "无效的电脑证书" }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { error("Client certificates are not accepted") }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val certificate = chain.firstOrNull() ?: error("Missing computer certificate")
                certificate.checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
                check(MessageDigest.isEqual(actual.toByteArray(), fingerprint.toByteArray())) { "电脑证书不匹配，请重新扫描二维码" }
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        val connection = network.openConnection(uri.toURL()) as HttpsURLConnection
        try {
            connection.sslSocketFactory = ssl.socketFactory
            connection.connectTimeout = 5000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val config = Base64.getUrlDecoder().decode(phoneLink.removePrefix("tapilot://pair/"))
            connection.setFixedLengthStreamingMode(config.size)
            connection.outputStream.use { it.write(config) }
            check(connection.responseCode == 200) { "电脑未接受配对信息，请刷新电脑配对窗口后重试" }
        } finally { connection.disconnect() }
    }
}
