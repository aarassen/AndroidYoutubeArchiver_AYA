package com.adnanearrassen.ytarchiver.server

import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory

/**
 * Provides an [SSLServerSocketFactory] backed by a self-signed certificate that
 * is generated once and persisted, so HTTPS is available with no bundled files.
 * Browsers will show a "not trusted" warning (expected for self-signed on a LAN);
 * the user proceeds once.
 */
object SelfSignedTls {

    private const val ALIAS = "ytarchiver"
    private val PASSWORD = "ytarchiver".toCharArray()

    fun serverSocketFactory(keystoreFile: File): SSLServerSocketFactory {
        val keyStore = if (keystoreFile.exists()) runCatching { load(keystoreFile) }.getOrNull()
            ?: generateAndStore(keystoreFile)
        else generateAndStore(keystoreFile)

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, PASSWORD)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, null, SecureRandom())
        return ctx.serverSocketFactory
    }

    private fun load(file: File): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, PASSWORD) }
        return ks
    }

    private fun generateAndStore(file: File): KeyStore {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 3600 * 1000)
        val notAfter = Date(now + 3650L * 24 * 3600 * 1000) // ~10 years
        val subject = X500Name("CN=YT Archiver")
        val builder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(now), notBefore, notAfter, subject, keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val cert: X509Certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, PASSWORD)
        ks.setKeyEntry(ALIAS, keyPair.private, PASSWORD, arrayOf(cert))
        file.outputStream().use { ks.store(it, PASSWORD) }
        Log.i("SelfSignedTls", "Generated self-signed certificate -> ${file.absolutePath}")
        return ks
    }
}
