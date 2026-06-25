package com.pedro.common.nal

import com.pedro.common.VideoCodec
import java.nio.ByteBuffer
import kotlin.experimental.and

object NalReader {
  private const val ZERO: Byte = 0x00
  private const val ONE: Byte = 0x01

  //H264
  private const val H264_SEI = 6
  private const val H264_AUD = 9
  private const val H264_SPS = 7
  private const val H264_PPS = 8

  //H265
  private const val H265_PRE_SEI = 39
  private const val H265_SU_SEI = 40
  private const val H265_AUD = 35
  private const val H265_SPS = 33
  private const val H265_PPS = 34
  private const val H265_VPS = 32

  fun extractNals(buffer: ByteBuffer, codec: VideoCodec, shouldDiscardVideoInfo: Boolean): ArrayList<ByteBuffer> {
    val units = ArrayList<ByteBuffer>()

    val array = buffer.array()
    val offset = buffer.arrayOffset()
    val start = offset + buffer.position()
    val limit = offset + buffer.limit()
    var payloadStart = -1

    var i = start
    while (i < limit - 2) {
      if (array[i] == ZERO && array[i + 1] == ZERO && array[i + 2] == ONE) {
        val previousPayloadEnd = if (i > start && array[i - 1] == ZERO) i - 1 else i
        if (payloadStart != -1 && previousPayloadEnd > payloadStart) {
          val duplicate = buffer.duplicate()
          duplicate.position(payloadStart - offset)
          duplicate.limit(previousPayloadEnd - offset)
          val nal = duplicate.slice()
          if (shouldKeepNal(nal, codec, shouldDiscardVideoInfo)) units.add(nal)
        }
        payloadStart = i + 3
        i += 3
      } else {
        i++
      }
    }
    if (payloadStart != -1 && payloadStart < limit) {
      val duplicate = buffer.duplicate()
      duplicate.position(payloadStart - offset)
      duplicate.limit(limit - offset)
      val nal = duplicate.slice()
      if (shouldKeepNal(nal, codec, shouldDiscardVideoInfo)) units.add(nal)
    }
    if (units.isEmpty()) units.add(buffer)
    return units
  }

  private fun shouldKeepNal(nal: ByteBuffer, codec: VideoCodec, shouldDiscardVideoInfo: Boolean): Boolean {
    return when (codec) {
      VideoCodec.H264 -> {
        val type = (nal.get(0) and 0x1F).toInt()
        !(type == H264_SEI || type == H264_AUD ||
            (shouldDiscardVideoInfo && (type == H264_SPS || type == H264_PPS)))
      }
      VideoCodec.H265 -> {
        val type = nal.get(0).toInt().shr(1) and 0x3F
        !(type == H265_PRE_SEI || type == H265_SU_SEI || type == H265_AUD ||
            (shouldDiscardVideoInfo && (type == H265_SPS || type == H265_PPS || type == H265_VPS)))
      }
      else -> false
    }
  }
}