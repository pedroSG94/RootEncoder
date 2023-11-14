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
