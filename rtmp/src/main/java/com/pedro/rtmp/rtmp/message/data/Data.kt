package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.RtmpMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
abstract class Data(private val name: String, timeStamp: Int, streamId: Int, basicHeader: BasicHeader): RtmpMessage(basicHeader) {

  private val data: MutableList<AmfData> = mutableListOf()
  private var bodySize = 0

  init {
    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    data.forEach {
      bodySize += it.getSize() + 1
    }
    header.messageLength = bodySize
    header.timeStamp = timeStamp
    header.messageStreamId = streamId
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  override fun readBody(input: InputStream) {
    bodySize = 0
    val amfString = AmfString()
    amfString.readHeader(input)
    amfString.readBody(input)
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

  override fun toString(): String {
    return "Data(name='$name', data=$data, bodySize=$bodySize)"
  }
}