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

package com.pedro.srt.mpeg2ts.psi

import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import java.nio.ByteBuffer

/**
 * Created by pedro on 24/8/23.
 *
 * SDT (Service Description Table)
 *
 * A type of PSI packet
 */
class Sdt(
  idExtension: Short,
  version: Byte,
  private val originalNetworkId: Short = 0xff01.toShort(),
  var service: Mpeg2TsService
) : Psi(
  pid = 17,
  id = 0x42,
  idExtension = idExtension,
  version = version,
  privateBit = true
) {

  override fun writeData(byteBuffer: ByteBuffer) {
    byteBuffer.putShort(originalNetworkId)
    byteBuffer.put(0b11111111.toByte())

    byteBuffer.putShort(service.id)
    byteBuffer.put(0b11111100.toByte()) // Reserved + EIT_schedule_flag + EIT_present_following_flag

    val serviceDescriptorLength = 3 + service.providerName.length + service.name.length
    val descriptorsLoopLength = 2 + serviceDescriptorLength // 2 = descriptor_tag + descriptor_length
    byteBuffer.putShort(
      ((0b1000 shl 12) // running_status - 4 -> running + free_CA_mode
          or (descriptorsLoopLength)).toShort()
    )

    // Service descriptor
    byteBuffer.put(0x48) // descriptor_tag
    byteBuffer.put(serviceDescriptorLength.toByte())

    byteBuffer.put(service.type)

    byteBuffer.put(service.providerName.length.toByte())
    byteBuffer.put(service.providerName.toByteArray(Charsets.UTF_8))

    byteBuffer.put(service.name.length.toByte())
    byteBuffer.put(service.name.toByteArray(Charsets.UTF_8))
  }

  override fun getTableDataSize(): Int {
    return 13 + service.providerName.length + service.name.length
  }
}