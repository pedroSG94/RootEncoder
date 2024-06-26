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

package com.pedro.srt.srt.packets.control.handshake.extension

import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.utils.EncryptInfo
import com.pedro.srt.utils.writeUInt16
import com.pedro.srt.utils.writeUInt32

/**
 * Created by pedro on 22/8/23.
 *
 */
data class HandshakeExtension(
  private val version: String = "1.5.3",
  private val flags: Int = ExtensionContentFlag.REXMITFLG.value or ExtensionContentFlag.CRYPT.value,
  private val receiverDelay: Int = 120,
  private val senderDelay: Int = 0,
  private val path: String = "",
  private val encryptInfo: EncryptInfo? = null
): SrtPacket() {

  fun write() {
    buffer.writeUInt16(ExtensionType.SRT_CMD_HS_REQ.value)
    //this extension contain a length of 3 with 4 bytes size block
    buffer.writeUInt16(3)
    buffer.write(getVersionData(version))
    buffer.writeUInt32(flags)
    buffer.writeUInt16(receiverDelay)
    buffer.writeUInt16(senderDelay)

    buffer.writeUInt16(ExtensionType.SRT_CMD_SID.value)
    val data = fixPathData(path.toByteArray(Charsets.UTF_8))
    buffer.writeUInt16(data.size / 4)
    buffer.write(data)
    //encrypted info
    if (encryptInfo != null) {
      buffer.writeUInt16(ExtensionType.SRT_CMD_KM_REQ.value)
      val keyMaterialMessage = KeyMaterialMessage(encryptInfo)
      val encryptedData = keyMaterialMessage.getData()
      buffer.writeUInt16(encryptedData.size / 4)
      buffer.write(encryptedData)
    }
  }

  private fun getVersionData(version: String): ByteArray {
    val versionNumbers = version.split(".").map { it.trim().toInt() }
    val versionValue = (versionNumbers[0] * 0x10000) + (versionNumbers[1] * 0x100) + versionNumbers[2]
    val bytes = ByteArray(4)

    bytes[0] = ((versionValue ushr 24) and 0xFF).toByte()
    bytes[1] = ((versionValue ushr 16) and 0xFF).toByte()
    bytes[2] = ((versionValue ushr 8) and 0xFF).toByte()
    bytes[3] = (versionValue and 0xFF).toByte()
    return bytes
  }

  /**
   * Fill array with 0 if length is not enough to fill blocks of 4 bytes
   * and convert it to little endian 32-bits
   */
  private fun fixPathData(data: ByteArray): ByteArray {
    val mod = data.size % 4
    return if (mod == 0) {
      reverseBlocks(data.asList())
    } else {
      val bytesToAdd = ByteArray(4 - mod) { 0x00 }.asList()
      val list = data.asList().toMutableList()
      list.addAll(bytesToAdd)
      reverseBlocks(list)
    }
  }

  private fun reverseBlocks(bytes: List<Byte>): ByteArray {
    val blocks = bytes.chunked(4)
    val result = mutableListOf<Byte>()
    blocks.map { it.reversed() }.forEach {
      result.addAll(it)
    }
    return result.toByteArray()
  }
}