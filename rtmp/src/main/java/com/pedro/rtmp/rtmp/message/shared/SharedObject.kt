package com.pedro.rtmp.rtmp.message.shared

import com.pedro.common.readUInt16
import com.pedro.common.readUInt32
import com.pedro.common.readUntil
import com.pedro.common.toUInt64
import com.pedro.common.writeUInt16
import com.pedro.common.writeUInt32
import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.amf.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import com.pedro.rtmp.rtmp.message.RtmpMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class SharedObject(
  private var name: String = "",
  private var version: Int = 0,
  private var flags: Long = 0L,
  private val events: MutableList<SharedObjectEvent> = mutableListOf()
): RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL.mark)) {

  private var bodySize = 0

  init {
    bodySize += 2 + name.toByteArray(Charsets.UTF_8).size + 4 + 8
    events.forEach { addEventSize(it) }
  }

  fun addEvent(event: SharedObjectEvent) {
    events.add(event)
    addEventSize(event)
  }

  private fun addEventSize(event: SharedObjectEvent) {
    bodySize += 1 + 4
    event.data.forEach { (key, value) ->
      bodySize += AmfString(key).getSize()
      bodySize += value.getSize() + 1
    }
  }

  override fun readBody(input: InputStream) {
    events.clear()
    bodySize = 0

    val nameLength = input.readUInt16()
    bodySize += 2
    val bytes = ByteArray(nameLength)
    input.readUntil(bytes)
    bodySize += nameLength
    name = String(bytes, Charsets.UTF_8)
    version = input.readUInt32()
    bodySize += 4
    val flagsBytes = ByteArray(8)
    input.readUntil(flagsBytes)
    flags = flagsBytes.toUInt64()
    bodySize += flagsBytes.size
    while (bodySize < header.messageLength) {
      val typeByte = input.read()
      bodySize += 1
      val type = SharedObjectEventType.values().find { it.value == typeByte } ?: SharedObjectEventType.UNKNOWN
      val data = linkedMapOf<String, AmfData>()
      val dataLength = input.readUInt32()
      bodySize += 4
      var dataRead = 0
      while (dataRead < dataLength && dataRead + bodySize < header.messageLength) {
        val key = AmfString().apply { readBody(input) }
        dataRead += key.getSize()
        val value = AmfData.getAmfData(input)
        dataRead += value.getSize() + 1
        data[key.value] = value
      }
      bodySize += dataRead
      events.add(SharedObjectEvent(type, data))
    }
    header.messageLength = bodySize
  }

  override fun storeBody(): ByteArray {
    val output = ByteArrayOutputStream()
    val nameBytes = name.toByteArray(Charsets.UTF_8)
    output.writeUInt16(nameBytes.size)
    output.write(nameBytes)
    output.writeUInt32(version)
    output.write(flags.toUInt64())
    events.forEach { event ->
      output.write(event.type.value)
      val dataLength = event.data.map {
        val keySize = AmfString(it.key).getSize()
        keySize + it.value.getSize() + 1
      }.sum()
      output.writeUInt32(dataLength)
      event.data.forEach { (key, value) ->
        AmfString(key).apply { writeBody(output) }
        value.writeHeader(output)
        value.writeBody(output)
      }
    }
    return output.toByteArray()
  }

  override fun getSize(): Int = bodySize

  override fun getType(): MessageType = MessageType.SHARED_OBJECT_AMF0

  override fun toString(): String {
    return "SharedObject(name='$name', version=$version, flags=$flags, events=$events, bodySize=$bodySize)"
  }
}