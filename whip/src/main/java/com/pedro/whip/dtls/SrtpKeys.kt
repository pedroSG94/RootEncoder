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

/**
 * Session keys derived from the same master key and master salt.
 * SRTP and SRTCP use different labels (RFC 3711 4.3.1 and 4.3.2)
 */
data class SrtpKeys(
  val rtp: CryptoProperties,
  val rtcp: CryptoProperties
)
