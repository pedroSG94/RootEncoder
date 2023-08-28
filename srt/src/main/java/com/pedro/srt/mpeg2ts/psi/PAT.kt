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
 * 47, 40, 00, 10, 00, 00, B0, 0D, FF, 93, C3, 00, 00, 46, 98, E0, 20, 62, FB, F7, 5B
 * 47, 40, 00, 10, 00, 00, B0, 0D, 00, 0B, C3, 00, 00, 46, 98, E0, 21, B4, 5E, 37, B8
 */
class PAT(
  idExtension: Short,
  version: Byte,
  service: Mpeg2TsService
) : PSI(
  pid = 0,
  id = 0x00,
  idExtension = idExtension,
  version = version,
) {

  private val programNum: Short = service.id
  private val reserved: Byte = 7
  private val programMapPid: Short = service.pcrPID ?: 0

  override fun writeData(byteBuffer: ByteBuffer) {
    byteBuffer.putShort(programNum)
    byteBuffer.putShort(((reserved.toInt() shl 13) or programMapPid.toInt()).toShort())
  }

  override fun getTableDataSize(): Int = 4


}