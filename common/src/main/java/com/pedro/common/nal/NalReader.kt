package com.pedro.common.nal

import java.nio.ByteBuffer

object NalReader {
  private const val ZERO: Byte = 0x00
  private const val ONE: Byte = 0x01

  fun extractNals(buffer: ByteBuffer): List<ByteBuffer> {
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
          units.add(duplicate.slice())
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
      units.add(duplicate.slice())
    }
    if (units.isEmpty()) units.add(buffer)
    return units
  }
}