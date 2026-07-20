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
    data.add(amfString)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    data.add(amfNumber)
    header.messageLength = bodySize
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  fun getStreamId(): Int {
    return (data[3] as AmfNumber).value.toInt()
  }

  fun getDescription(): String {
    return ((data[3] as AmfObject).getProperty("description") as AmfString).value
  }

  fun getCode(): String {
    return ((data[3] as AmfObject).getProperty("code") as AmfString).value
  }

  override fun readBody(input: InputStream) {
    data.clear()
    var bytesRead = 0
    while (bytesRead < header.messageLength) {
      val amfData = AmfData.getAmfData(input)
      bytesRead += amfData.getSize() + 1
      data.add(amfData)
    }
    if (data.isNotEmpty()) {
      if (data[0] is AmfString) {
        name = (data[0] as AmfString).value
      }
      if (data.size >= 2 && data[1] is AmfNumber) {
        commandId = (data[1] as AmfNumber).value.toInt()
      }
    }
    bodySize = bytesRead
    header.messageLength = bodySize
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
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