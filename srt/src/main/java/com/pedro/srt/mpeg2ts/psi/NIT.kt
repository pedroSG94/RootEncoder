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

import java.nio.ByteBuffer

/**
 * Created by pedro on 24/8/23.
 *
 * NIT (Network Information Table)
 *
 * A type of PSI packet
 *
 * This table is undefined on ISO/IEC 13818-1
 */
class NIT(
  id: Byte,
  idExtension: Short,
  version: Byte,
) : PSI(
  pid = 0,
  id = id,
  idExtension = idExtension,
  version = version,
) {

  override fun writeData(byteBuffer: ByteBuffer) {
    TODO("Not yet implemented")
  }

  override fun getTableDataSize(): Int {
    TODO("Not yet implemented")
  }
}