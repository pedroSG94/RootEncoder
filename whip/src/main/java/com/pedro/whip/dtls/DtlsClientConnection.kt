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
import org.bouncycastle.tls.AlertDescription
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.DTLSClientProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SRTPProtectionProfile
import org.bouncycastle.tls.SignatureAlgorithm
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsExtensionsUtils
import org.bouncycastle.tls.TlsFatalAlert
import org.bouncycastle.tls.TlsSRTPUtils
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.TlsUtils
import org.bouncycastle.tls.UseSRTPData
import java.util.Hashtable

/**
 * DTLS client role, used when the remote answer is a=setup:passive so we must start the handshake.
 */
class DtlsClientConnection(
  private val certificate: DtlsCertificate,
  private val remoteFingerprint: String
) : DefaultTlsClient(certificate.crypto), DtlsConnection {

  private val srtpProfile = SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80
  private var dtlsTransport: DTLSTransport? = null
  private var exportedSrtpKeys: ByteArray? = null

  override fun start(transport: DtlsTransport, callback: DtlsConnection.Callback) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        dtlsTransport = DTLSClientProtocol().connect(this@DtlsClientConnection, transport)
        val keys = exportedSrtpKeys
          ?: throw IllegalStateException("SRTP keying material not captured during handshake")
        callback.onHandshakeComplete(DtlsUtils.deriveSrtpKeys(keys, isClient = true))
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

  override fun getClientExtensions(): Hashtable<*, *> {
    val extensions = TlsExtensionsUtils.ensureExtensionsInitialised(super.getClientExtensions())
    TlsSRTPUtils.addUseSRTPExtension(extensions, UseSRTPData(intArrayOf(srtpProfile), TlsUtils.EMPTY_BYTES))
    return extensions
  }

  override fun processServerExtensions(serverExtensions: Hashtable<*, *>?) {
    super.processServerExtensions(serverExtensions)
    //the server must accept use_srtp with our profile, if not we can't derive the SRTP keys
    val useSrtp = TlsSRTPUtils.getUseSRTPExtension(serverExtensions)
      ?: throw TlsFatalAlert(AlertDescription.handshake_failure, "server doesn't support use_srtp")
    if (useSrtp.protectionProfiles.size != 1 || useSrtp.protectionProfiles[0] != srtpProfile) {
      throw TlsFatalAlert(AlertDescription.illegal_parameter, "invalid SRTP protection profile")
    }
  }

  override fun getAuthentication(): TlsAuthentication {
    val context = context
    return object : TlsAuthentication {
      override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
        DtlsUtils.verifyFingerprint(serverCertificate.certificate, remoteFingerprint)
      }

      //WebRTC requires mutual authentication, the server validates our fingerprint
      override fun getClientCredentials(certificateRequest: CertificateRequest): TlsCredentials {
        val sigAlgs = certificateRequest.supportedSignatureAlgorithms
          ?: TlsUtils.getDefaultSupportedSignatureAlgorithms(context)
        return DtlsUtils.buildSignerCredentials(context, certificate, sigAlgs, SignatureAlgorithm.rsa)
      }
    }
  }

  override fun notifyHandshakeComplete() {
    super.notifyHandshakeComplete()
    try {
      exportedSrtpKeys = DtlsUtils.exportSrtpKeys(context)
    } catch (_: Exception) { }
  }
}
