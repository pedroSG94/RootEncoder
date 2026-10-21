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

/**
 * DTLS handshake for DTLS-SRTP. The role depends on the a=setup value of the remote answer:
 * active -> the remote starts the handshake and we are the server ([DtlsServerConnection])
 * passive -> we start the handshake and we are the client ([DtlsClientConnection])
 */
interface DtlsConnection {

  interface Callback {
    /**
     * @param keys used to protect the packets that we send (depends on our DTLS role)
     */
    fun onHandshakeComplete(keys: SrtpKeys)
    fun onHandshakeFailed(reason: String?)
  }

  fun start(transport: DtlsTransport, callback: Callback)

  fun close()
}
