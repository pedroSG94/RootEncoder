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

package com.pedro.srt.utils

import com.pedro.srt.srt.packets.control.handshake.extension.CipherType
import com.pedro.srt.srt.packets.data.KeyBasedEncryption

/**
 * Created by pedro on 13/11/23.
 */
data class EncryptInfo(
  val keyBasedEncryption: KeyBasedEncryption,
  val cipher: CipherType,
  val salt: ByteArray,
  val key: ByteArray,
  val keyLength: Int
)
