/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.srt.srt.packets.control.handshake

import java.io.IOException

/**
 * Created by pedro on 22/8/23.
 */
enum class HandshakeType(val value: Int) {
  DONE(4294967293u.toInt()), AGREEMENT(4294967294u.toInt()), CONCLUSION(4294967295u.toInt()), WAVE_A_HAND(0), INDUCTION(1),
  //errors
  SRT_REJ_UNKNOWN(1000), SRT_REJ_SYSTEM(1001), SRT_REJ_PEER(1002), SRT_REJ_RESOURCE(1003), SRT_REJ_ROGUE(1004),
  SRT_REJ_BACKLOG(1005), SRT_REJ_IPE(1006), SRT_REJ_CLOSE(1007), SRT_REJ_VERSION(1008), SRT_REJ_RDVCOOKIE(1009),
  SRT_REJ_BADSECRET(1010), SRT_REJ_UNSECURE(1011), SRT_REJ_MESSAGEAPI(1012), SRT_REJ_CONGESTION(1013),
  SRT_REJ_FILTER(1014), SRT_REJ_GROUP(1015), SRT_REJ_TIMEOUT(1016), SRT_REJ_CRYPTO(1017);

  companion object {
    infix fun from(value: Int): HandshakeType = entries.firstOrNull { it.value == value } ?: throw IOException("unknown handshake type: $value")
  }
}