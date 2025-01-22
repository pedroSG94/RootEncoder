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

  @JvmField
  val lock = Any()
  var trackAudio = 1
  var trackVideo = 0
  const val clockVideoFrequency = 90000L
  const val RTP_HEADER_LENGTH = 12
  const val MTU = 1500
  const val REPORT_PACKET_LENGTH = 28
  const val payloadType = 96
  //PCMA, https://blog.csdn.net/hiwubihe/article/details/84569152
  const val payloadTypeG711 = 8
  //H264 IDR
  const val IDR = 5

  //H265 IDR
  const val IDR_N_LP = 20
  const val IDR_W_DLP = 19
}