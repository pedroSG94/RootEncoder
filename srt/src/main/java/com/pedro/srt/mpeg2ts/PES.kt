/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.srt.mpeg2ts

/**
 * Created by pedro on 20/8/23.
 *
 * PES (Packetized Elementary Stream)
 *
 * Header (6 bytes):
 *
 * Packet start code prefix -> 3 bytes
 * Stream id -> 1 byte Examples: Audio streams (0xC0-0xDF), Video streams (0xE0-0xEF) [4][5]
 * PES Packet length -> 2 bytes
 *
 * Optional
 * PES header -> variable length (length >= 3) 	not present in case of Padding stream & Private stream 2 (navigation data)
 * Data -> Variable
 */
class PES {
  private val startCodePrefix: Int = 0x000001
  private val streamId: Byte = PesType.AUDIO.value
  private val length: Short = 0
  private val data: ByteArray = byteArrayOf()
}