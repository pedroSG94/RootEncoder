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
 * PAT (Program Association Table)
 *
 * A type of PSI packet
 *
 * Program num -> 16 bits
 * Reserved bits -> 3 bits
 * Program map PID -> 13 bits
 *
 */
class Pat(
  idExtension: Short,
  version: Byte,
  var service: Mpeg2TsService
) : Psi(
  pid = 0,
  id = 0x00,
  idExtension = idExtension,
  version = version,
) {

  private val reserved: Byte = 7

  override fun writeData(byteBuffer: ByteBuffer) {
    val programNum: Short = service.id
    val programMapPid: Short = (service.pmt?.pid ?: 0).toShort()
    byteBuffer.putShort(programNum)
    byteBuffer.putShort(((reserved.toInt() shl 13) or programMapPid.toInt()).toShort())
  }

  override fun getTableDataSize(): Int = 4


}