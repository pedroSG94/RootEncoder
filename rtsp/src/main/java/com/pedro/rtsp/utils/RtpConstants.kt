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

package com.pedro.rtsp.utils

/**
 * Created by pedro on 19/02/17.
 */
object RtpConstants {

  const val MAX_SEQ_NUMBER = 65535
  const val HMAC_SIZE = 10
  const val clockVideoFrequency = 90000L
  //RFC 7587. Opus always use a 48khz clock in RTP, no matter the sample rate used to encode
  const val clockOpusFrequency = 48000L
  //RFC 3551. The payload type 8 (PCMA) is statically mapped to 8khz mono
  const val clockG711Frequency = 8000L
  const val RTP_HEADER_LENGTH = 12
  const val MTU = 1500
  const val REPORT_PACKET_LENGTH = 28
  const val payloadType = 96
  //RFC 3551, table 4. Static payload type mapped to PCMA (G711A), 8khz and 1 channel
  const val payloadTypeG711 = 8
  //H264 IDR
  const val IDR = 5

  //H265 IDR
  const val IDR_N_LP = 20
  const val IDR_W_DLP = 19
}