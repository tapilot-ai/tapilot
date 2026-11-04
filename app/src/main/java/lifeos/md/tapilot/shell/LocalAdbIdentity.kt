package lifeos.md.tapilot.shell

import android.content.Context
import android.util.AtomicFile
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** Private app storage, excluded from backup. Pairing is not consent to start the helper. */
internal class LocalAdbIdentity(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "local-adb-identity"))
    val key: PrivateKey
    val certificate: Certificate
    init {
        if (file.baseFile.exists()) {
            file.openRead().use { input ->
                val data = java.io.DataInputStream(input)
                val size = data.readInt()
                require(size in 256..8192)
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(ByteArray(size).also(data::readFully)))
                certificate = CertificateFactory.getInstance("X.509").generateCertificate(data)
            }
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            key = pair.private
            val provider = BouncyCastleProvider()
            val subject = X500Name("CN=Tapilot local helper")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(subject, BigInteger(128, SecureRandom()),
                Date(now - 60_000), Date(now + 3650L * 86_400_000), subject, pair.public)
            certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(
                builder.build(JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(key)))
            val output = file.startWrite()
            try {
                val data = java.io.DataOutputStream(output)
                data.writeInt(key.encoded.size); data.write(key.encoded); data.write(certificate.encoded); data.flush()
                file.finishWrite(output)
            } catch (failure: Exception) { file.failWrite(output); throw failure }
        }
    }
    companion object {
        fun forget(context: Context) = AtomicFile(File(context.noBackupFilesDir, "local-adb-identity")).delete()
    }
}
