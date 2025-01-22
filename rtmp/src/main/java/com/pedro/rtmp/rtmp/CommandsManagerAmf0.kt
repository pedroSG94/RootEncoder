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

package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfEcmaArray
import com.pedro.rtmp.amf.v0.AmfNull
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfStrictArray
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.flv.audio.AudioFormat
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import com.pedro.rtmp.utils.socket.RtmpSocket

class CommandsManagerAmf0: CommandsManager() {
  override suspend fun sendConnectImp(auth: String, socket: RtmpSocket) {
    val connect = CommandAmf0("connect", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    val connectInfo = AmfObject()
    connectInfo.setProperty("app", appName + auth)
    connectInfo.setProperty("flashVer", flashVersion)
    connectInfo.setProperty("tcUrl", tcUrl + auth)
    if (!videoDisabled) {
      if (videoCodec == VideoCodec.H265) {
        val list = mutableListOf<AmfData>()
        list.add(AmfString("hvc1"))
        val array = AmfStrictArray(list)
        connectInfo.setProperty("fourCcList", array)
      } else if (videoCodec == VideoCodec.AV1) {
        val list = mutableListOf<AmfData>()
        list.add(AmfString("av01"))
        val array = AmfStrictArray(list)
        connectInfo.setProperty("fourCcList", array)
      }
    }
    connectInfo.setProperty("objectEncoding", 0.0)
    connect.addData(connectInfo)

    connect.writeHeader(socket)
    connect.writeBody(socket)
    sessionHistory.setPacket(commandId, "connect")
    Log.i(TAG, "send $connect")
  }

  override suspend fun createStreamImp(socket: RtmpSocket) {
    val releaseStream = CommandAmf0("releaseStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    releaseStream.addData(AmfNull())
    releaseStream.addData(AmfString(streamName))

    releaseStream.writeHeader(socket)
    releaseStream.writeBody(socket)
    sessionHistory.setPacket(commandId, "releaseStream")
    Log.i(TAG, "send $releaseStream")

    val fcPublish = CommandAmf0("FCPublish", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    fcPublish.addData(AmfNull())
    fcPublish.addData(AmfString(streamName))

    fcPublish.writeHeader(socket)
    fcPublish.writeBody(socket)
    sessionHistory.setPacket(commandId, "FCPublish")
    Log.i(TAG, "send $fcPublish")

    val createStream = CommandAmf0("createStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    createStream.addData(AmfNull())

    createStream.writeHeader(socket)
    createStream.writeBody(socket)
    sessionHistory.setPacket(commandId, "createStream")
    Log.i(TAG, "send $createStream")
  }

  override suspend fun sendMetadataImp(socket: RtmpSocket) {
    val name = "@setDataFrame"
    val metadata = DataAmf0(name, getCurrentTimestamp(), streamId)
    metadata.addData(AmfString("onMetaData"))
    val amfEcmaArray = AmfEcmaArray()
    amfEcmaArray.setProperty("duration", 0.0)
    if (!videoDisabled) {
      amfEcmaArray.setProperty("width", width.toDouble())
      amfEcmaArray.setProperty("height", height.toDouble())
      //few servers don't support it even if it is in the standard rtmp enhanced
      val codecValue = when (videoCodec) {
        VideoCodec.H264 -> VideoFormat.AVC.value
        VideoCodec.H265 -> VideoFormat.HEVC.value
        VideoCodec.AV1 -> VideoFormat.AV1.value
      }
      amfEcmaArray.setProperty("videocodecid", codecValue.toDouble())
      amfEcmaArray.setProperty("framerate", fps.toDouble())
      amfEcmaArray.setProperty("videodatarate", 0.0)
    }
    if (!audioDisabled) {
      val codecValue = when (audioCodec) {
        AudioCodec.G711 -> AudioFormat.G711_A.value
        AudioCodec.AAC -> AudioFormat.AAC.value
        AudioCodec.OPUS -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
      }
      amfEcmaArray.setProperty("audiocodecid", codecValue.toDouble())
      amfEcmaArray.setProperty("audiosamplerate", sampleRate.toDouble())
      amfEcmaArray.setProperty("audiosamplesize", 16.0)
      amfEcmaArray.setProperty("audiodatarate", 0.0)
      amfEcmaArray.setProperty("stereo", isStereo)
    }
    amfEcmaArray.setProperty("filesize", 0.0)
    metadata.addData(amfEcmaArray)

    metadata.writeHeader(socket)
    metadata.writeBody(socket)
    Log.i(TAG, "send $metadata")
  }

  override suspend fun sendPublishImp(socket: RtmpSocket) {
    val name = "publish"
    val publish = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    publish.addData(AmfNull())
    publish.addData(AmfString(streamName))
    publish.addData(AmfString("live"))

    publish.writeHeader(socket)
    publish.writeBody(socket)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $publish")
  }

  override suspend fun sendCloseImp(socket: RtmpSocket) {
    val name = "closeStream"
    val closeStream = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId, BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    closeStream.addData(AmfNull())

    closeStream.writeHeader(socket)
    closeStream.writeBody(socket)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $closeStream")
  }
}