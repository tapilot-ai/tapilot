package lifeos.md.tapilot.wireless

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

internal class WirelessTls {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    // Conscrypt hashes the TLS transcript itself before invoking the Keystore signer.
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(X500Principal("CN=Tapilot Wireless"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(0)).setCertificateNotAfter(Date(4_102_444_800_000L)).build())
                generateKeyPair()
            }
        }
    }
    val fingerprint: String = MessageDigest.getInstance("SHA-256").digest(store.getCertificate(ALIAS).encoded)
        .joinToString("") { "%02x".format(it) }

    fun context(): SSLContext = SSLContext.getInstance("TLSv1.2").apply {
        init(arrayOf(object : X509ExtendedKeyManager() {
            override fun getPrivateKey(alias: String?): PrivateKey = store.getKey(ALIAS, null) as PrivateKey
            override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(store.getCertificate(ALIAS) as X509Certificate)
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?): String? = if (type == "EC") ALIAS else null
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = if (type == "EC") arrayOf(ALIAS) else null
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        }), null, null)
    }
    companion object { private const val ALIAS = "tapilot-wireless-tls-ec-v1" }
}
