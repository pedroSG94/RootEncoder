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
import org.bouncycastle.tls.DTLSServerProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DefaultTlsServer
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

/**
 * Created by pedro on 21/7/25.
 */
class DtlsConnection(crypto: BcTlsCrypto): DefaultTlsServer(crypto) {

  private var connection: DTLSTransport? = null
  private var transport: DtlsTransport? = null

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

  fun getCryptoProperties(): Map<String, String> {
    return mapOf()
  }

//  fun extractCryptoProps(): Array<Properties?> {
//    val p = arrayOfNulls<Properties>(2)
//    val empty: ByteArray? = ByteArray(0)
//    // assume SRTP_AES128_CM_HMAC_SHA1_80
//    val keyLength = 16
//    val saltLength = 14
//    val ts = 2 * (keyLength + saltLength)
//    val keys: ByteArray = context.exportKeyingMaterial("EXTRACTOR-dtls_srtp", null, ts)
//    val clientKeyParams = ByteArray(30)
//    val serverKeyParams = ByteArray(30)
//    var offs = 0
//    System.arraycopy(keys, offs, clientKeyParams, 0, keyLength)
//    offs += keyLength
//    System.arraycopy(keys, offs, serverKeyParams, 0, keyLength)
//    offs += keyLength
//    System.arraycopy(keys, offs, clientKeyParams, keyLength, saltLength)
//    offs += saltLength
//    System.arraycopy(keys, offs, serverKeyParams, keyLength, saltLength)
//    offs += saltLength
//
//    val client: String = kotlin.text.String(Base64Coder.encode(clientKeyParams))
//    val server: String = kotlin.text.String(Base64Coder.encode(serverKeyParams))
//
//    p[0] = Properties()
//    p[0]!!.put("required", "1")
//    p[0]!!.put("crypto-suite", "AES_CM_128_HMAC_SHA1_80")
//    p[0]!!.put("key-params", "inline:" + client)
//    p[1] = Properties()
//    p[1]!!.put("required", "1")
//    p[1]!!.put("crypto-suite", "AES_CM_128_HMAC_SHA1_80")
//    p[1]!!.put("key-params", "inline:" + server)
//    return p
//  }
}