package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.amf.AmfNumber
import com.pedro.rtmp.amf.AmfObject
import com.pedro.rtmp.amf.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class Command(var name: String = "", var commandId: Int = 0, private val timeStamp: Int = 0, private val streamId: Int = 0, basicHeader: BasicHeader =
    BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)): RtmpMessage(basicHeader) {

  private var bodySize = 0
  private val data: MutableList<AmfData> = mutableListOf()

  init {
    header.timeStamp = timeStamp
    header.messageStreamId = streamId

    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    header.messageLength = bodySize
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  fun getStreamId(): Int {
    return (data[1] as AmfNumber).value.toInt()
  }

  fun getDescription() = getProperty("description")

  fun getCode() = getProperty("code")

  private fun getProperty(key: String): String {
    return data.filterIsInstance<AmfObject>().firstNotNullOfOrNull {
      it.getProperty(key) as? AmfString
    }?.value ?: ""
  }

  override fun readBody(input: InputStream) {
    data.clear()
    bodySize = 0

    val nameData = AmfData.getAmfData(input)
    if (nameData is AmfString) name = nameData.value
    bodySize += nameData.getSize() + 1
    if (bodySize >= header.messageLength) return
    val commandData = AmfData.getAmfData(input)
    if (commandData is AmfNumber) commandId = commandData.value.toInt()
    bodySize += commandData.getSize() + 1

    while (bodySize < header.messageLength) {
      val amfData = AmfData.getAmfData(input)
      data.add(amfData)
      bodySize += amfData.getSize() + 1
    }
    header.messageLength = bodySize
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val amfString = AmfString(name)
    amfString.writeHeader(byteArrayOutputStream)
    amfString.writeBody(byteArrayOutputStream)
    val amfNumber = AmfNumber(commandId.toDouble())
    amfNumber.writeHeader(byteArrayOutputStream)
    amfNumber.writeBody(byteArrayOutputStream)
    data.forEach {
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getSize(): Int = bodySize

  override fun getType(): MessageType = MessageType.COMMAND_AMF0

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timeStamp, streamId=$streamId, data=$data, bodySize=$bodySize)"
  }
}