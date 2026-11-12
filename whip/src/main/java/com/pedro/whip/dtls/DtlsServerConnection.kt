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
import org.bouncycastle.tls.ProtocolName
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsCredentialedDecryptor
import org.bouncycastle.tls.TlsCredentialedSigner
import org.bouncycastle.tls.TlsSRTPUtils
import org.bouncycastle.tls.TlsUtils
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor
import java.nio.ByteBuffer
import java.util.Hashtable
import java.util.Vector

/**
 * Created by pedro on 21/7/25.
 */
class DtlsServerConnection(
  private val certificate: DtlsCertificate,
  private val remoteFingerprint: String
) : DefaultTlsServer(certificate.crypto), DtlsConnection {

  private val wantRTP = true
  private var dtlsTransport: DTLSTransport? = null
  private var exportedSrtpKeys: ByteArray? = null

  override fun start(transport: DtlsTransport, callback: DtlsConnection.Callback) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        dtlsTransport = DTLSServerProtocol().accept(this@DtlsServerConnection, transport)
        val keys = exportedSrtpKeys
          ?: throw IllegalStateException("SRTP keying material not captured during handshake")
        callback.onHandshakeComplete(DtlsUtils.deriveSrtpKeys(keys, isClient = false))
      } catch (e: Exception) {
        callback.onHandshakeFailed(e.message)
      }
    }
  }

  override fun close() {
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
    val sigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context)
    return DtlsUtils.buildSignerCredentials(context, certificate, sigAlgs, signatureType)
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
    DtlsUtils.verifyFingerprint(clientCertificate, remoteFingerprint)
  }

  override fun processClientExtensions(clientExtensions: Hashtable<*, *>?) {
    TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
    super.processClientExtensions(clientExtensions)
  }

  override fun notifyHandshakeComplete() {
    super.notifyHandshakeComplete()
    try {
      exportedSrtpKeys = DtlsUtils.exportSrtpKeys(context)
    } catch (_: Exception) { }
  }
}
