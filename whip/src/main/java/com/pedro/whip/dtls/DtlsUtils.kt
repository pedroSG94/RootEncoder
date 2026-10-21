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
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.ExporterLabel
import org.bouncycastle.tls.HashAlgorithm
import org.bouncycastle.tls.SignatureAndHashAlgorithm
import org.bouncycastle.tls.TlsContext
import org.bouncycastle.tls.TlsCredentialedSigner
import org.bouncycastle.tls.crypto.TlsCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Vector
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Code shared by the DTLS client and server roles.
 */
object DtlsUtils {

  private const val KEY_LENGTH = 16
  private const val SALT_LENGTH = 14

  fun verifyFingerprint(certificate: Certificate, remoteFingerprint: String) {
    val certs = certificate.certificateList
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

  fun buildSignerCredentials(
    context: TlsContext, certificate: DtlsCertificate,
    signatureAlgorithms: Vector<*>, signatureType: Short
  ): TlsCredentialedSigner {
    val certif = Certificate(arrayOf(certificate.certificate))
    val cryptoParams = TlsCryptoParameters(context)
    val sigAlgs = signatureAlgorithms.filterIsInstance<SignatureAndHashAlgorithm>()
    val sigAlg = sigAlgs.firstOrNull { it.signature == signatureType && it.hash == HashAlgorithm.sha256 }
      ?: sigAlgs.firstOrNull { it.signature == signatureType }
      ?: sigAlgs.first()
    return BcDefaultTlsCredentialedSigner(cryptoParams, certificate.crypto, certificate.key, certif, sigAlg)
  }

  /**
   * exportKeyingMaterial() is only valid inside notifyHandshakeComplete callback
   */
  fun exportSrtpKeys(context: TlsContext): ByteArray {
    return context.exportKeyingMaterial(ExporterLabel.dtls_srtp, null, 2 * (KEY_LENGTH + SALT_LENGTH))
  }

  /**
   * RFC 5764 4.2: exported keying material layout for AES_CM_128_HMAC_SHA1_80
   * [clientMasterKey(16) | serverMasterKey(16) | clientMasterSalt(14) | serverMasterSalt(14)]
   * The client protects the packets that it sends with the client keys and the server with the server keys.
   *
   * @param isClient true if we are the DTLS client
   * @return keys used to protect the packets that we send
   */
  fun deriveSrtpKeys(exportedKeys: ByteArray, isClient: Boolean): SrtpKeys {
    val keyOffset = if (isClient) 0 else KEY_LENGTH
    val saltOffset = 2 * KEY_LENGTH + if (isClient) 0 else SALT_LENGTH
    val masterKey = exportedKeys.copyOfRange(keyOffset, keyOffset + KEY_LENGTH)
    val masterSalt = exportedKeys.copyOfRange(saltOffset, saltOffset + SALT_LENGTH)
    return SrtpKeys(
      rtp = deriveCryptoProperties(masterKey, masterSalt, rtcp = false),
      rtcp = deriveCryptoProperties(masterKey, masterSalt, rtcp = true)
    )
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

  // RFC 3711 4.3.1: derive SRTP session keys from master key + master salt via AES-CM PRF.
  // SRTP labels: 0x00 → cipher key, 0x01 → auth key, 0x02 → session salt
  // SRTCP labels (RFC 3711 4.3.2): 0x03 → cipher key, 0x04 → auth key, 0x05 → session salt
  // x = master_salt with byte[7] XOR'd with the label (label * 2^48 in 112-bit representation)
  private fun deriveCryptoProperties(masterKey: ByteArray, masterSalt: ByteArray, rtcp: Boolean): CryptoProperties {
    val base = if (rtcp) 0x03 else 0x00
    val cipherKey = deriveKey(masterKey, masterSalt, (base + 0).toByte(), 16)
    val authKey = deriveKey(masterKey, masterSalt, (base + 1).toByte(), 20)
    val salt = deriveKey(masterKey, masterSalt, (base + 2).toByte(), 14)
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
