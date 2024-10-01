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

package com.pedro.rtsp.rtp.packets

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.setLong
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by pedro on 27/11/18.
 */
abstract class BasePacket(private var clock: Long, private val payloadType: Int) {

  protected var channelIdentifier: Int = 0
  private var seq = 0L
  private var ssrc = 0L
  protected val maxPacketSize = RtpConstants.MTU - 28
  protected val TAG = "BasePacket"

  abstract suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  )

  open fun reset() {
    seq = 0
    ssrc = 0
  }

  fun setSSRC(ssrc: Long) {
    this.ssrc = ssrc
  }

  protected fun setClock(clock: Long) {
    this.clock = clock
  }

  protected fun getBuffer(size: Int): ByteArray {
    val buffer = ByteArray(size)
    buffer[0] = 0x80.toByte()
    buffer[1] = payloadType.toByte()
    setLongSSRC(buffer, ssrc)
    requestBuffer(buffer)
    return buffer
  }

  protected fun updateTimeStamp(buffer: ByteArray, timestamp: Long): Long {
    val ts = timestamp * clock / 1000000000L
    buffer.setLong(ts, 4, 8)
    return ts
  }

  protected fun updateSeq(buffer: ByteArray) {
    buffer.setLong(++seq, 2, 4)
  }

  protected fun markPacket(buffer: ByteArray) {
    buffer[1] = buffer[1] or 0x80.toByte()
  }

  private fun setLongSSRC(buffer: ByteArray, ssrc: Long) {
    buffer.setLong(ssrc, 8, 12)
  }

  private fun requestBuffer(buffer: ByteArray) {
    buffer[1] = buffer[1] and 0x7F
  }
}