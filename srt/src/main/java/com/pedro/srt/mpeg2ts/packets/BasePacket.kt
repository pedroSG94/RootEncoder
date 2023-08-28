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

package com.pedro.srt.mpeg2ts.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.srt.mpeg2ts.AdaptationField
import com.pedro.srt.mpeg2ts.AdaptationFieldControl
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.PID
import com.pedro.srt.mpeg2ts.psi.PSIManager
import com.pedro.srt.mpeg2ts.psi.TableToSend
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 *
 * ISO/IEC 13818-1
 *
 * Header (4 bytes):
 *
 * Sync byte -> 8 bits
 * Transport error indicator (TEI) -> 1 bit
 * Payload unit start indicator (PUSI) -> 1 bit
 * Transport priority -> 1 bit
 * PID -> 13 bits
 * Transport scrambling control (TSC) -> 2 bits
 * Adaptation field control -> 2 bits
 * Continuity counter -> 4 bits
 *
 * Optional fields
 * Adaptation field -> variable
 * Payload data -> variable
 */
abstract class BasePacket(
  val psiManager: PSIManager
) {

  var configSend = false
  private val syncByte: Byte = 0x47
  private val transportErrorIndicator: Boolean = false
  private val transportPriority: Boolean = false
  private val transportScramblingControl: Byte = 0 //2 bits
  protected val pid = PID.generatePID()

  abstract fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  )

  private fun checkSendPsi(isKey: Boolean): TableToSend {
      return psiManager.shouldSend(isKey)
  }

  protected fun writeTsHeader(
    buffer: ByteBuffer,
    payloadUnitStartIndicator: Boolean, //is the first chunk of the frame
    pid: Short,
    adaptationFieldControl: AdaptationFieldControl,
    continuityCounter: Byte,
    adaptationField: AdaptationField?
  ) {
    buffer.put(syncByte)
    val combined: Short = ((transportErrorIndicator.toInt() shl 15)
        or (payloadUnitStartIndicator.toInt() shl 14)
        or (transportPriority.toInt() shl 13) or pid.toInt()).toShort()
    buffer.putShort(combined)
    val combined2: Byte = ((transportScramblingControl.toInt() and 0x3 shl 6)
    or (adaptationFieldControl.value.toInt() and 0x3 shl 4) or (continuityCounter.toInt() and 0xF)).toByte()
    buffer.put(combined2)
    adaptationField?.getData()?.let {
      buffer.put(it)
    }
  }

  private fun writePat(buffer: ByteBuffer) {
    writeTsHeader(buffer, true, pid, AdaptationFieldControl.PAYLOAD, 0, null)
    val pat = psiManager.getPat()
    val psiSize = getTSPacketSize() - getTSPacketHeaderSize()
    pat.write(buffer, psiSize)
  }

  private fun writePmt(buffer: ByteBuffer) {
    val pmt = psiManager.getPmt()
    writeTsHeader(buffer, true, pmt.pid, AdaptationFieldControl.PAYLOAD, 0, null)
    val psiSize = getTSPacketSize() - getTSPacketHeaderSize()
    pmt.write(buffer, psiSize)
  }

  private fun writeSdt(buffer: ByteBuffer) {
    val sdt = psiManager.getSdt()
    writeTsHeader(buffer, true, sdt.pid, AdaptationFieldControl.PAYLOAD, 0, null)
    val psiSize = getTSPacketSize() - getTSPacketHeaderSize()
    sdt.write(buffer, psiSize)
  }

  protected fun checkSendTable(sizeLimit: Int, callback: (MpegTsPacket) -> Unit) {
    val buffer = ByteBuffer.allocate(sizeLimit)
    val tableToSend = checkSendPsi(false)
    when (tableToSend) {
      TableToSend.PAT_PMT -> {
        writePat(buffer)
        val pat = buffer.array().sliceArray(0 until buffer.position())
        buffer.rewind()
        callback(MpegTsPacket(pat, false))
        writePmt(buffer)
        val pmt = buffer.array().sliceArray(0 until buffer.position())
        buffer.rewind()
        callback(MpegTsPacket(pmt, false))
      }
      TableToSend.SDT -> {
        writeSdt(buffer)
        val sdt = buffer.array().sliceArray(0 until buffer.position())
        buffer.rewind()
        callback(MpegTsPacket(sdt, false))
      }
      TableToSend.NONE -> {}
      TableToSend.ALL -> {
        sendConfig(sizeLimit, callback)
      }
    }
  }

  protected fun sendConfig(sizeLimit: Int, callback: (MpegTsPacket) -> Unit) {
    val buffer = ByteBuffer.allocate(sizeLimit)
    writeSdt(buffer)
    val sdt = buffer.array().sliceArray(0 until buffer.position())
    buffer.rewind()
    callback(MpegTsPacket(sdt, false))
    writePat(buffer)
    val pat = buffer.array().sliceArray(0 until buffer.position())
    buffer.rewind()
    callback(MpegTsPacket(pat, false))
    writePmt(buffer)
    val pmt = buffer.array().sliceArray(0 until buffer.position())
    buffer.rewind()
    callback(MpegTsPacket(pmt, false))
  }

  protected fun getRealSize(limitSize: Int): Int {
    var count = getTSPacketSize()
    while (count <= limitSize) {
      count += getTSPacketSize()
    }
    return count - getTSPacketSize()
  }

  fun getTSPacketHeaderSize() = 4

  fun getTSPacketSize() = 188

  fun reset() {
    configSend = false
  }
}