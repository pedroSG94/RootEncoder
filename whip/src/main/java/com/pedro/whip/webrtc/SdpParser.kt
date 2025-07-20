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

package com.pedro.whip.webrtc

import java.lang.IllegalArgumentException

/**
 * Created by pedro on 16/7/25.
 */
object SdpParser {
  fun parseBodyAnswer(body: String): SdpInfo {
    val uFrag = extractContent(body, "a=ice-ufrag:")
    val uPass = extractContent(body, "a=ice-pwd:")
    val fingerprint = extractContent(body, "a=fingerprint:sha-256")
    val candidates = extractCandidates(body)
    return SdpInfo(uFrag, uPass, fingerprint, candidates)
  }

  private fun extractContent(sdp: String, content: String): String {
    return sdp.lines()
      .map { it.trim() }.last { it.startsWith(content, ignoreCase = true) }.removePrefix(content).trim()
  }

  private fun extractCandidates(sdp: String): List<Candidate> {
    val prefix = "a=candidate:"
    return sdp.lines()
      .map { it.trim() }.filter { it.startsWith(prefix, ignoreCase = true) }
      .map { it.removePrefix(prefix) }.mapNotNull { candidateText ->
        val values = candidateText.split(" ")
        if (values.size >= 8) {
          val protocol = values[1].toInt() // 1 SRTP, 2 SRTCP
          val priority = values[3].toInt()
          val address = values[4]
          val port = values[5].toInt()
          val candidateTypeText = values[7]
          val type = CandidateType.entries.find { it.value == candidateTypeText }
            ?: throw IllegalArgumentException("Unknown candidate type: $candidateTypeText")
          if (values.size >= 10) {
            val localAddress = values[9]
            val localPort = values[11].toInt()
            Candidate(type, protocol, priority, localAddress, localPort, address, port)
          } else {
            Candidate(type, protocol, priority, address, port, null, null)
          }
        } else null
      }
  }
}