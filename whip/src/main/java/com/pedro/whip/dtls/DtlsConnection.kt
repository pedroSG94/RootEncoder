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

import android.util.Log
import com.pedro.rtsp.utils.CryptoProperties
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
import java.util.Vector
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

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
  private var exportedSrtpKeys: ByteArray? = null

  fun start(transport: DtlsTransport, callback: Callback) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        dtlsTransport = DTLSServerProtocol().accept(this@DtlsConnection, transport)
        val keys = exportedSrtpKeys
          ?: throw IllegalStateException("SRTP keying material not captured during handshake")
        val props = deriveCryptoPropertiesFromExport(keys)
        callback.onHandshakeComplete(props)
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

  // exportKeyingMaterial() is only valid inside this callback — capture it here.
  override fun notifyHandshakeComplete() {
    super.notifyHandshakeComplete()
    try {
      val keyLength = 16
      val saltLength = 14
      exportedSrtpKeys = context.exportKeyingMaterial(
        "EXTRACTOR-dtls_srtp", null, 2 * (keyLength + saltLength)
      )
    } catch (_: Exception) { }
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

  private fun deriveCryptoPropertiesFromExport(keys: ByteArray): List<CryptoProperties> {
    // RFC 5764: exported keying material layout for AES_CM_128_HMAC_SHA1_80
    // [clientMasterKey(16) | serverMasterKey(16) | clientMasterSalt(14) | serverMasterSalt(14)]
    val keyLength = 16
    val saltLength = 14
    val clientMasterKey = ByteArray(keyLength)
    val serverMasterKey = ByteArray(keyLength)
    val clientMasterSalt = ByteArray(saltLength)
    val serverMasterSalt = ByteArray(saltLength)
    var offs = 0
    System.arraycopy(keys, offs, clientMasterKey, 0, keyLength); offs += keyLength
    System.arraycopy(keys, offs, serverMasterKey, 0, keyLength); offs += keyLength
    System.arraycopy(keys, offs, clientMasterSalt, 0, saltLength); offs += saltLength
    System.arraycopy(keys, offs, serverMasterSalt, 0, saltLength)
    return listOf(
      deriveCryptoProperties(clientMasterKey, clientMasterSalt),
      deriveCryptoProperties(serverMasterKey, serverMasterSalt)
    )
  }

  // RFC 3711 4.3.1: derive SRTP session keys from master key + master salt via AES-CM PRF.
  // label 0x00 → cipher key, 0x01 → auth key, 0x02 → session salt
  // x = master_salt with byte[7] XOR'd with the label (label * 2^48 in 112-bit representation)
  private fun deriveCryptoProperties(masterKey: ByteArray, masterSalt: ByteArray): CryptoProperties {
    val cipherKey = deriveKey(masterKey, masterSalt, 0x00.toByte(), 16)
    val authKey = deriveKey(masterKey, masterSalt, 0x01.toByte(), 20)
    val salt = deriveKey(masterKey, masterSalt, 0x02.toByte(), 14)
    return CryptoProperties(authKey, cipherKey, salt)
  }

  private fun deriveKey(masterKey: ByteArray, masterSalt: ByteArray, label: Byte, lengthBytes: Int): ByteArray {
    val x = masterSalt.copyOf(14)
    x[7] = (x[7].toInt() xor label.toInt()).toByte()
    val iv = ByteArray(16).also { System.arraycopy(x, 0, it, 0, 14) }
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"))
    val result = ByteArray(lengthBytes)
    var offset = 0
    var counter = 0
    while (offset < lengthBytes) {
      iv[14] = (counter shr 8).toByte()
      iv[15] = counter.toByte()
      val block = cipher.doFinal(iv)
      val toCopy = minOf(16, lengthBytes - offset)
      System.arraycopy(block, 0, result, offset, toCopy)
      offset += toCopy
      counter++
    }
    return result
  }
}
