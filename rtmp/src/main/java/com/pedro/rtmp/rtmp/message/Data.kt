package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.amf.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class Data(private var name: String = "", timeStamp: Int = 0, streamId: Int = 0, basicHeader: BasicHeader = BasicHeader(
  ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)) :
  RtmpMessage(basicHeader) {

  private var bodySize = 0
  private val data: MutableList<AmfData> = mutableListOf()

  init {
    header.timeStamp = timeStamp
    header.messageStreamId = streamId

    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    header.messageLength = bodySize
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  override fun readBody(input: InputStream) {
    data.clear()
    bodySize = 0
    val amfString = AmfString()
    amfString.readHeader(input)
    amfString.readBody(input)
    name = amfString.value
    bodySize += amfString.getSize() + 1
    while (bodySize < header.messageLength) {
      val amfData = AmfData.getAmfData(input)
      data.add(amfData)
      bodySize += amfData.getSize() + 1
    }
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val amfString = AmfString(name)
    amfString.writeHeader(byteArrayOutputStream)
    amfString.writeBody(byteArrayOutputStream)
    data.forEach {
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getSize(): Int = bodySize

  override fun getType(): MessageType = MessageType.DATA_AMF0

  override fun toString(): String {
    return "Data(name='$name', data=$data, bodySize=$bodySize)"
  }
}