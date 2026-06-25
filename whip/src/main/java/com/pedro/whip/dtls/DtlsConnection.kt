/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.whip.dtls

import com.pedro.rtsp.utils.CryptoProperties
import com.pedro.rtsp.utils.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.ClientCertificateType
import org.bouncycastle.tls.DTLSServerProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DefaultTlsServer
import org.bouncycastle.tls.ExtensionType
import org.bouncycastle.tls.HashAlgorithm
import org.bouncycastle.tls.ProtocolName
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SignatureAndHashAlgorithm
import org.bouncycastle.tls.TlsCredentialedDecryptor
import org.bouncycastle.tls.TlsCredentialedSigner
import org.bouncycastle.tls.TlsSRTPUtils
import org.bouncycastle.tls.TlsUtils
import org.bouncycastle.tls.crypto.TlsCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Hashtable
import java.util.Properties
import java.util.Vector

/**
 * Created by pedro on 21/7/25.
 */
class DtlsConnection(
  private val certificate: DtlsCertificate,
  private val remoteFingerprint: String
) : DefaultTlsServer(certificate.crypto) {

  interface Callback {
    fun onHandshakeComplete(properties: List<CryptoProperties>)
    fun onHandshakeFailed(reason: String?)
  }

  private val wantRTP = true
  private var dtlsTransport: DTLSTransport? = null

  fun start(transport: DtlsTransport, callback: Callback) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        dtlsTransport = DTLSServerProtocol().accept(this@DtlsConnection, transport)
        callback.onHandshakeComplete(extractCryptoProperties())
      } catch (e: Exception) {
        callback.onHandshakeFailed(e.message)
      }
    }
  }

  fun close() {
    runCatching { dtlsTransport?.close() }
  }

  override fun getSupportedVersions(): Array<ProtocolVersion> =
    arrayOf(ProtocolVersion.DTLSv12)

  override fun getCertificateRequest(): CertificateRequest {
    val types = shortArrayOf(
      ClientCertificateType.rsa_sign,
      ClientCertificateType.dss_sign,
      ClientCertificateType.ecdsa_sign
    )
    val sigAlgs = if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(serverVersion)) {
      TlsUtils.getDefaultSupportedSignatureAlgorithms(context)
    } else null
    return CertificateRequest(types, sigAlgs, null)
  }

  override fun getProtocolNames(): Vector<ProtocolName?> =
    Vector<ProtocolName?>(1).apply { add(ProtocolName.WEBRTC) }

  override fun getRSAEncryptionCredentials(): TlsCredentialedDecryptor {
    val certif = Certificate(arrayOf(certificate.certificate))
    return BcDefaultTlsCredentialedDecryptor(certificate.crypto, certif, certificate.key)
  }

  override fun getRSASignerCredentials(): TlsCredentialedSigner =
    buildSignerCredentials(ClientCertificateType.rsa_sign)

  override fun getECDSASignerCredentials(): TlsCredentialedSigner =
    buildSignerCredentials(ClientCertificateType.ecdsa_sign)

  private fun buildSignerCredentials(signatureType: Short): TlsCredentialedSigner {
    val certif = Certificate(arrayOf(certificate.certificate))
    val cryptoParams = TlsCryptoParameters(context)
    val sigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context) as Vector<Any>
    val sigAlg = sigAlgs.filterIsInstance<SignatureAndHashAlgorithm>()
      .firstOrNull { it.signature == signatureType && it.hash == HashAlgorithm.sha256 }
      ?: sigAlgs.first() as SignatureAndHashAlgorithm
    return BcDefaultTlsCredentialedSigner(cryptoParams, certificate.crypto, certificate.key, certif, sigAlg)
  }

  override fun getServerExtensions(): Hashtable<*, *>? {
    var ret = super.getServerExtensions()
    if (wantRTP && clientExtensions?.containsKey(ExtensionType.use_srtp) == true) {
      val prof = ByteArray(5)
      ByteBuffer.wrap(prof).apply {
        putChar(2.toChar())
        put(byteArrayOf(0x00, 0x01))
        put(0.toByte())
      }
      if (ret == null) ret = Hashtable<Any?, Any?>()
      (ret as Hashtable<Any?, Any?>)[ExtensionType.use_srtp] = prof
    }
    return ret
  }

  override fun notifyClientCertificate(clientCertificate: Certificate) {
    val certs = clientCertificate.certificateList
    if (certs.isNullOrEmpty()) throw IOException("no certs offered")
    try {
      validateX509(certs[0])
      val fingerprint = computeFingerprint(certs[0])
      if (!fingerprint.equals(remoteFingerprint, ignoreCase = true)) {
        throw IOException("fingerprints don't match")
      }
    } catch (e: CertificateException) {
      throw IOException("offered cert is invalid: ${e.message}")
    }
  }

  override fun processClientExtensions(clientExtensions: Hashtable<*, *>?) {
    TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
    super.processClientExtensions(clientExtensions)
  }

  private fun validateX509(cert: TlsCertificate) {
    val cf = CertificateFactory.getInstance("X.509")
    val bis = ByteArrayInputStream(cert.encoded)
    while (bis.available() > 0) {
      (cf.generateCertificate(bis) as X509Certificate).checkValidity()
    }
  }

  private fun computeFingerprint(cert: TlsCertificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    return digest.joinToString(":") { "%02X".format(it) }
  }

  fun extractCryptoProperties(): List<CryptoProperties> {
    // assume SRTP_AES128_CM_HMAC_SHA1_80
    val keyLength = 16
    val saltLength = 14
    val ts = 2 * (keyLength + saltLength)
    val keys: ByteArray = context.exportKeyingMaterial("EXTRACTOR-dtls_srtp", null, ts)
    val clientKey = ByteArray(keyLength)
    val serverKey = ByteArray(keyLength)
    val serverSalt = ByteArray(saltLength)
    val clientSalt = ByteArray(saltLength)
    var offs = 0
    System.arraycopy(keys, offs, clientKey, 0, keyLength)
    offs += keyLength
    System.arraycopy(keys, offs, serverKey, 0, keyLength)
    offs += keyLength
    System.arraycopy(keys, offs, clientSalt, keyLength, saltLength)
    offs += saltLength
    System.arraycopy(keys, offs, serverSalt, keyLength, saltLength)
    offs += saltLength

    val suite = "AES_CM_128_HMAC_SHA1_80"
    val clientCrypto = CryptoProperties(clientKey, clientKey, clientSalt)
    val serverCrypto = CryptoProperties(serverKey, serverKey, serverSalt)
    return listOf(clientCrypto, serverCrypto)
  }
}
