package com.pedro.library.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.isKeyframe
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.common.toUInt24
import com.pedro.common.toUInt32
import com.pedro.encoder.video.VideoEncoderHelper
import com.pedro.library.base.recording.AsyncBaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import com.pedro.rtmp.amf.AmfEcmaArray
import com.pedro.rtmp.amf.AmfString
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AudioFormat
import com.pedro.rtmp.flv.audio.packet.AacPacket
import com.pedro.rtmp.flv.audio.packet.G711Packet
import com.pedro.rtmp.flv.audio.packet.OpusPacket
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.packet.Av1Packet
import com.pedro.rtmp.flv.video.packet.H264Packet
import com.pedro.rtmp.flv.video.packet.H265Packet
import com.pedro.rtmp.flv.video.packet.Vp8Packet
import com.pedro.rtmp.flv.video.packet.Vp9Packet
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class FlvMuxerRecordController: AsyncBaseRecordController() {

    private var outputStream: OutputStream? = null
    private var videoPacket: BasePacket? = null
    private var audioPacket: BasePacket? = null
    //metadata config
    private var width = 0
    private var height = 0
    private var fps = 0
    private var sampleRate = 0
    private var isStereo = true
    private var sendInfo = false

    override fun startRecordImp(path: String, listener: RecordController.Listener?, tracks: RecordTracks) {
        outputStream = FileOutputStream(path)
        start(listener)
    }

    override fun startRecordImp(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordTracks) {
        outputStream = FileOutputStream(fd)
        start(listener)
    }

    private fun start(listener: RecordController.Listener?) {
        audioPacket = when (getAudioCodec()) {
            AudioCodec.G711 -> G711Packet()
            AudioCodec.AAC, AudioCodec.HE_AAC -> AacPacket().apply { sendAudioInfo(sampleRate, isStereo) }
            AudioCodec.OPUS -> OpusPacket().apply { sendAudioInfo(sampleRate, isStereo) }
        }
        videoPacket = when (getVideoCodec()) {
            VideoCodec.H264 -> H264Packet()
            VideoCodec.H265 -> H265Packet()
            VideoCodec.AV1 -> Av1Packet()
            VideoCodec.VP8 -> Vp8Packet()
            VideoCodec.VP9 -> Vp9Packet()
        }
        outputStream?.let {
            try {
                it.write(createFlvFileHeader())
                writeFlvFileMetadata(it)
            } catch (_: Exception) {}
        }
        if (tracks == RecordTracks.AUDIO) recordStatus = RecordController.Status.RECORDING
        listener?.onStatusChange(recordStatus)
    }

    override fun stopRecordImp() {
        videoPacket?.reset(false)
        audioPacket?.reset(false)
        try {
            outputStream?.close()
        } catch (_: Exception) { } finally {
            outputStream = null
        }
        sendInfo = false
    }

  override suspend fun onWriteFrame(frame: MediaFrame) {
    when (frame.type) {
      MediaFrame.Type.VIDEO -> {
        if (tracks != RecordTracks.AUDIO) {
          if (recordStatus == RecordController.Status.STARTED) {
            getVideoInfo(frame.data, frame.info.toMediaCodecBufferInfo())
          } else if (recordStatus == RecordController.Status.RECORDING) {
            videoPacket?.createFlvPacket(frame) { packet ->
              outputStream?.let { writeFlvPacket(it, packet) }
            }
          }
        }
      }
      MediaFrame.Type.AUDIO -> {
        if (recordStatus == RecordController.Status.RECORDING && tracks != RecordTracks.VIDEO) {
          audioPacket?.createFlvPacket(frame) { packet ->
            outputStream?.let { writeFlvPacket(it, packet) }
          }
        }
      }
    }
  }

    private fun getVideoInfo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.isKeyframe() || isKeyFrame(buffer)) {
            if (!sendInfo) {
                when (videoPacket) {
                    is H264Packet -> {
                        val buffers =
                            VideoEncoderHelper.decodeSpsPpsFromBuffer(buffer.duplicate(), info.size)
                        if (buffers != null) {
                            Log.i(TAG, "manual sps/pps extraction success")
                            val oldSps = buffers.first
                            val oldPps = buffers.second
                            (videoPacket as H264Packet).sendVideoInfo(oldSps, oldPps)
                            sendInfo = true
                        } else {
                            Log.e(TAG, "manual sps/pps extraction failed")
                        }
                    }
                    is H265Packet -> {
                        val byteBufferList = VideoEncoderHelper.extractVpsSpsPpsFromH265(buffer.duplicate())
                        if (byteBufferList.size == 3) {
                            Log.i(TAG, "manual vps/sps/pps extraction success")
                            val oldSps = byteBufferList[1]
                            val oldPps = byteBufferList[2]
                            val oldVps = byteBufferList[0]
                            (videoPacket as H265Packet).sendVideoInfo(oldSps, oldPps, oldVps)
                            sendInfo = true
                        } else {
                            Log.e(TAG, "manual vps/sps/pps extraction failed")
                        }
                    }
                    is Av1Packet -> {
                        val obuSequence = VideoEncoderHelper.extractObuSequence(buffer.duplicate(), info)
                        if (obuSequence != null) {
                            (videoPacket as Av1Packet).sendVideoInfo(obuSequence)
                            sendInfo = true
                        } else {
                            Log.e(TAG, "manual av1 extraction failed")
                        }
                    }
                    is Vp8Packet -> sendInfo = true
                    is Vp9Packet -> {
                        val header = VideoEncoderHelper.extractVp9BitStreamHeader(buffer.duplicate(), info)
                        if (header != null) {
                            (videoPacket as Vp9Packet).sendVideoInfo(header)
                            sendInfo = true
                        } else {
                            Log.e(TAG, "manual vp9 extraction failed")
                        }
                    }
                    else -> {
                        Log.e(TAG, "Unsupported codec: ${videoPacket?.javaClass?.name ?: "null"}")
                    }
                }
            }
            if (sendInfo && recordStatus == RecordController.Status.STARTED) {
                myRequestKeyFrame = null
                recordStatus = RecordController.Status.RECORDING
                listener?.onStatusChange(recordStatus)
            }
        } else if (myRequestKeyFrame != null) {
            myRequestKeyFrame?.onRequestKeyFrame()
            myRequestKeyFrame = null
        }
    }

    override fun setVideoFormat(videoFormat: MediaFormat) {
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val fps = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        this.width = width
        this.height = height
        this.fps = fps
        when (videoPacket) {
            is H264Packet -> {
                val sps = videoFormat.getByteBuffer("csd-0")
                val pps = videoFormat.getByteBuffer("csd-1")
                if (sps != null && pps != null) {
                    (videoPacket as H264Packet).sendVideoInfo(sps.duplicate(), pps.duplicate())
                    sendInfo = true
                }
            }
            is H265Packet -> {
               val bufferInfo = videoFormat.getByteBuffer("csd-0")
               if (bufferInfo != null) {
                   val byteBufferList = VideoEncoderHelper.extractVpsSpsPpsFromH265(bufferInfo.duplicate())
                   if (byteBufferList.size == 3) {
                       val sps = byteBufferList[1]
                       val pps = byteBufferList[2]
                       val vps = byteBufferList[0]
                       (videoPacket as H265Packet).sendVideoInfo(sps, pps, vps)
                       sendInfo = true
                   }
               }
            }
            is Av1Packet -> {
                val bufferInfo = videoFormat.getByteBuffer("csd-0")
                if (bufferInfo != null && bufferInfo.remaining() > 4) {
                    (videoPacket as Av1Packet).sendVideoInfo(bufferInfo.duplicate())
                    sendInfo = true
                }
            }
            is Vp8Packet -> sendInfo = true
        }
        if (sendInfo && recordStatus == RecordController.Status.STARTED) {
            myRequestKeyFrame = null
            recordStatus = RecordController.Status.RECORDING
            listener?.onStatusChange(recordStatus)
        }
    }

    override fun setAudioFormat(audioFormat: MediaFormat) {
        val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        this.sampleRate = sampleRate
        this.isStereo = channels > 1
        (audioPacket as? AacPacket)?.sendAudioInfo(sampleRate, isStereo)
    }

    override fun resetFormats() {
    }

    private fun createFlvFileHeader(): ByteArray {
        val flag: Byte = if (tracks == RecordTracks.AUDIO) 0x04 else if (tracks == RecordTracks.VIDEO) 0x01 else 0x05
        return byteArrayOf(
            0x46, 0x4C, 0x56, // "FLV"
            0x01, // Versión 1
            flag,
            0x00, 0x00, 0x00, 0x09,
            0x00, 0x00, 0x00, 0x00
        )
    }

    private fun writeFlvFileMetadata(outputStream: OutputStream) {
        val head = AmfString("onMetaData")
        val info = AmfEcmaArray()
        info.setProperty("width", width.toDouble())
        info.setProperty("height", height.toDouble())
        val videoCodecValue = when (getVideoCodec()) {
            VideoCodec.H264 -> VideoFormat.AVC.value
            VideoCodec.H265 -> VideoFormat.HEVC.value
            VideoCodec.VP8 -> VideoFormat.VP8.value
            VideoCodec.VP9 -> VideoFormat.VP9.value
            VideoCodec.AV1 -> VideoFormat.AV1.value
        }
        info.setProperty("videocodecid", videoCodecValue.toDouble())
        info.setProperty("framerate", fps.toDouble())
        val audioCodecValue = when (getAudioCodec()) {
            AudioCodec.AAC, AudioCodec.HE_AAC -> AudioFormat.AAC.value
            AudioCodec.G711 -> AudioFormat.G711_A.value
            AudioCodec.OPUS -> AudioFormat.OPUS.value
        }
        info.setProperty("audiocodecid", audioCodecValue.toDouble())
        info.setProperty("audiosamplerate", sampleRate.toDouble())
        info.setProperty("audiosamplesize", 16.0)
        info.setProperty("stereo", isStereo)

        val output = ByteArrayOutputStream()
        head.writeHeader(output)
        head.writeBody(output)
        info.writeHeader(output)
        info.writeBody(output)

        val data = output.toByteArray()
        val flvHeaderTag = createHeaderTag(0x12, data.size, 0)
        val flvTagSize = (flvHeaderTag.size + data.size).toUInt32()

        try {
            outputStream.write(flvHeaderTag)
            outputStream.write(data)
            outputStream.write(flvTagSize)
        } catch (_: Exception) {}
    }

    private fun writeFlvPacket(outputStream: OutputStream, flvPacket: FlvPacket) {
        val type: Byte = when (flvPacket.type) {
            FlvType.AUDIO -> 0x08
            FlvType.VIDEO -> 0x09
        }
        val flvHeaderTag = createHeaderTag(type, flvPacket.length, flvPacket.timeStamp)
        val flvTagSize = (flvHeaderTag.size + flvPacket.length).toUInt32()

        try {
            outputStream.write(flvHeaderTag)
            outputStream.write(flvPacket.buffer)
            outputStream.write(flvTagSize)
        } catch (_: Exception) {}
    }

    private fun createHeaderTag(type: Byte, length: Int, timeStamp: Long): ByteArray {
        return byteArrayOf(type)
            .plus(length.toUInt24())
            .plus(timeStamp.toInt().toUInt24())
            .plus((timeStamp shr 24).toByte())
            .plus(byteArrayOf(0x00, 0x00, 0x00))
    }
}