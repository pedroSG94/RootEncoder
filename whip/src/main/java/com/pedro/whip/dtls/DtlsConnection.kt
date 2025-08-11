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
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.whip.dtls.test.DTLS
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.ClientCertificateType
import org.bouncycastle.tls.DTLSServerProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DefaultTlsServer
import org.bouncycastle.tls.ExtensionType
import org.bouncycastle.tls.ProtocolName
import org.bouncycastle.tls.TlsContext
import org.bouncycastle.tls.TlsCredentialedDecryptor
import org.bouncycastle.tls.TlsSRTPUtils
import org.bouncycastle.tls.TlsUtils
import org.bouncycastle.tls.crypto.TlsCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor
import java.io.IOException
import java.nio.ByteBuffer
import java.security.cert.CertificateException
import java.util.Hashtable
import java.util.Vector

/**
 * Created by pedro on 21/7/25.
 */
class DtlsConnection(private val dtlsCertificate: DtlsCertificate): DefaultTlsServer(dtlsCertificate.crypto) {

  private var connection: DTLSTransport? = null
  private var transport: DtlsTransport? = null
  private var verified = false
  private val wantRTP = true

  fun connect(socket: UdpStreamSocket) {
    val dtlsServerProtocol = DTLSServerProtocol()
    val transport = DtlsTransport(socket).apply { open() }
    this.transport = transport
    connection = dtlsServerProtocol.accept(this, transport)
    Log.i("Pedro", "dtls connected!!!")
  }

  fun close() {
    connection?.close()
    transport?.close()
  }

  fun enqueue(bytes: ByteArray) {
    transport?.enqueue(bytes)
  }

  @Throws(IOException::class)
  override fun getCertificateRequest(): CertificateRequest {
    val certificateTypes = shortArrayOf(
      ClientCertificateType.rsa_sign,
      ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign
    )

    var serverSigAlgs: Vector<*>? = null
    if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(this.getServerVersion())) {
      serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context)
    }

    val certificateAuthorities: Vector<*>? = null
    return CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities)
  }

  public override fun getProtocolNames(): Vector<ProtocolName?> {
    val ret = Vector<ProtocolName?>(1)
    ret.add(ProtocolName.WEBRTC)
    return ret
  }

  override fun getRSAEncryptionCredentials(): TlsCredentialedDecryptor {
    val tcs = arrayOfNulls<TlsCertificate>(1)
    tcs[0] = dtlsCertificate.certificate
    val certif = Certificate(tcs)
    val ret = BcDefaultTlsCredentialedDecryptor(dtlsCertificate.crypto, certif, dtlsCertificate.key)
    return ret
  }

  @Throws(IOException::class)
  override fun getServerExtensions(): Hashtable<*, *>? {
    // see https://tools.ietf.org/html/rfc5764
    val SRTP_AES128_CM_HMAC_SHA1_80 = byteArrayOf(0x00, 0x01)
    var ret = super.getServerExtensions()

    val prof = ByteArray(5)

    val profileB = ByteBuffer.wrap(prof)
    profileB.putChar(2.toChar()) // length;
    profileB.put(SRTP_AES128_CM_HMAC_SHA1_80)
    profileB.put(0.toByte()) // mkti
    if (wantRTP) {
      if ((this.clientExtensions != null) && this.clientExtensions.containsKey(ExtensionType.use_srtp)) {
        if (ret == null) {
          ret = Hashtable<Any?, Any?>()
        }
        ret.put(ExtensionType.use_srtp, prof)
      }
    }
    return ret
  }

  @Throws(IOException::class)
  override fun notifyClientCertificate(clientCertificate: Certificate) {
    val cs = clientCertificate.certificateList
    if ((cs == null) || (cs.size < 1)) {
      throw IOException("no certs offered")
    }
    try {
      if (DTLS.validate(cs[0])) {
        val ffp = DTLS.getPrint(cs[0], true)
        if (!ffp.equals(dtlsCertificate.fingerprint, ignoreCase = true)) {
          throw IOException("fingerprints don't match ")
        }
        verified = true
      } else {
        throw IOException("offered cert is empty ")
      }
    } catch (ex: CertificateException) {
      throw IOException("offered cert is invalid :" + ex.message)
    }
  }

  @Throws(IOException::class)
  override fun processClientExtensions(clientExtensions: Hashtable<*, *>?) {
    val d = TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
    super.processClientExtensions(clientExtensions)
  }


  fun getContext(): TlsContext = context
}