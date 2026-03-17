package com.pedro.library.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.encoder.video.VideoEncoderHelper
import com.pedro.library.base.recording.AsyncBaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegTsPacketizer
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pid
import com.pedro.srt.mpeg2ts.packets.AacPacket
import com.pedro.srt.mpeg2ts.packets.BasePacket
import com.pedro.srt.mpeg2ts.packets.H26XPacket
import com.pedro.srt.mpeg2ts.packets.OpusPacket
import com.pedro.srt.mpeg2ts.psi.Psi
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.chunkPackets
import com.pedro.srt.utils.toCodec
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class Mpeg2TsMuxerRecordController : AsyncBaseRecordController() {

  private var outputStream: OutputStream? = null

  //metadata config
  private var service = Mpeg2TsService()
  private val psiManager = PsiManager(service).apply {
    upgradePatVersion()
    upgradeSdtVersion()
  }
  private val limitSize: Int
    get() {
      return Constants.MTU - SrtPacket.headerSize
    }

  private val mpegTsPacketizer = MpegTsPacketizer(psiManager)
  private var audioPacket: BasePacket = AacPacket(limitSize, psiManager)
  private val videoPacket = H26XPacket(limitSize, psiManager)
  private val chunkSize = limitSize / MpegTsPacketizer.packetSize
  private var sampleRate = 0
  private var isStereo = true
  private var sendInfo = false

  @Throws(IOException::class)
  override fun startRecordImp(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    outputStream = FileOutputStream(path)
    start(listener, tracks)
  }

  @Throws(IOException::class)
  override fun startRecordImp(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    outputStream = FileOutputStream(fd)
    start(listener, tracks)
  }

  @Throws(IOException::class)
  private fun start(listener: RecordController.Listener?, tracks: RecordTracks) {
    audioPacket = when (getAudioCodec()) {
      AudioCodec.AAC -> AacPacket(limitSize, psiManager).apply {
        sendAudioInfo(
          sampleRate,
          isStereo
        )
      }
      AudioCodec.OPUS -> OpusPacket(limitSize, psiManager)
      else -> {
        throw IOException("Unsupported AudioCodec: " + getAudioCodec().name)
      }
    }
    if (getVideoCodec() == VideoCodec.AV1) {
      throw IOException("Unsupported VideoCodec: " + getVideoCodec().name)
    }
    audioPacket.setLimitSize(limitSize)
    videoPacket.setLimitSize(limitSize)
    outputStream?.let {
      val videoEnabled = tracks == RecordTracks.VIDEO || tracks == RecordTracks.ALL
      val audioEnabled = tracks == RecordTracks.AUDIO || tracks == RecordTracks.ALL
      setTrackConfig(videoEnabled, audioEnabled)

      val psiList = mutableListOf<Psi>(psiManager.getPat())
      psiManager.getPmt()?.let { psiList.add(0, it) }
      psiList.add(psiManager.getSdt())
      val psiPacketsConfig = mpegTsPacketizer.write(psiList).chunkPackets(chunkSize).map { buffer ->
        MpegTsPacket(buffer, MpegType.PSI, PacketPosition.SINGLE, isKey = false)
      }
      writePackets(psiPacketsConfig)
    }
    if (tracks == RecordTracks.AUDIO) recordStatus = RecordController.Status.RECORDING
    listener?.onStatusChange(recordStatus)
  }

  private fun setTrackConfig(videoEnabled: Boolean, audioEnabled: Boolean) {
    Pid.reset()
    service.clearTracks()
    if (audioEnabled) service.addTrack(getAudioCodec().toCodec())
    if (videoEnabled) service.addTrack(getVideoCodec().toCodec())
    service.generatePmt()
    psiManager.updateService(service)
  }

  override fun stopRecordImp() {
    psiManager.reset()
    mpegTsPacketizer.reset()
    audioPacket.reset(false)
    videoPacket.reset(false)
    try {
      outputStream?.close()
    } catch (_: Exception) {
    } finally {
      outputStream = null
    }
    sendInfo = false
  }

  private suspend fun getMpegTsPackets(
    mediaFrame: MediaFrame?,
    callback: suspend (List<MpegTsPacket>) -> Unit
  ) {
    if (mediaFrame == null) return
    when (mediaFrame.type) {
      MediaFrame.Type.VIDEO -> videoPacket.createAndSendPacket(mediaFrame) { callback(it) }
      MediaFrame.Type.AUDIO -> audioPacket.createAndSendPacket(mediaFrame) { callback(it) }
    }
  }

  private fun writePackets(mpegTsPackets: List<MpegTsPacket>) {
    try {
      outputStream?.let { outputStream ->
        mpegTsPackets.forEach { mpegTsPacket ->
          outputStream.write(mpegTsPacket.buffer)
        }
      }
    } catch (_: Exception) { }
  }

  override suspend fun onWriteFrame(frame: MediaFrame) {
    when (frame.type) {
      MediaFrame.Type.VIDEO -> {
        if (tracks != RecordTracks.AUDIO) {
          if (recordStatus == RecordController.Status.STARTED) {
            getVideoInfo(frame.data, frame.info.toMediaCodecBufferInfo())
          } else if (recordStatus == RecordController.Status.RECORDING) {
            writeMpeg2TsPacket(frame)
          }
        }
      }
      MediaFrame.Type.AUDIO -> {
        if (recordStatus == RecordController.Status.RECORDING && tracks != RecordTracks.VIDEO) {
          writeMpeg2TsPacket(frame)
        }
      }
    }
  }

  private suspend fun writeMpeg2TsPacket(frame: MediaFrame) {
    getMpegTsPackets(frame) { mpegTsPackets ->
      val isKey = mpegTsPackets[0].isKey
      val psiPackets = psiManager.checkSendInfo(isKey, mpegTsPacketizer, chunkSize)
      writePackets(psiPackets)
      writePackets(mpegTsPackets)
    }
  }

  private fun getVideoInfo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || isKeyFrame(buffer)) {
      if (!sendInfo) {
        when (getVideoCodec()) {
          VideoCodec.H264 -> {
            val buffers =
              VideoEncoderHelper.decodeSpsPpsFromBuffer(buffer.duplicate(), info.size)
            if (buffers != null) {
              Log.i(TAG, "manual sps/pps extraction success")
              val oldSps = buffers.first
              val oldPps = buffers.second
              videoPacket.sendVideoInfo(oldSps, oldPps, null)
              sendInfo = true
            } else {
              Log.e(TAG, "manual sps/pps extraction failed")
            }
          }

          VideoCodec.H265 -> {
            val byteBufferList = VideoEncoderHelper.extractVpsSpsPpsFromH265(buffer.duplicate())
            if (byteBufferList.size == 3) {
              Log.i(TAG, "manual vps/sps/pps extraction success")
              val oldSps = byteBufferList[1]
              val oldPps = byteBufferList[2]
              val oldVps = byteBufferList[0]
              videoPacket.sendVideoInfo(oldSps, oldPps, oldVps)
              sendInfo = true
            } else {
              Log.e(TAG, "manual vps/sps/pps extraction failed")
            }
          }

          else -> {
            Log.e(TAG, "Unsupported codec: ${getVideoCodec()}")
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
    when (getVideoCodec()) {
      VideoCodec.H264 -> {
        val sps = videoFormat.getByteBuffer("csd-0")
        val pps = videoFormat.getByteBuffer("csd-1")
        if (sps != null && pps != null) {
          videoPacket.sendVideoInfo(sps.duplicate(), pps.duplicate(), null)
          sendInfo = true
        }
      }

      VideoCodec.H265 -> {
        val bufferInfo = videoFormat.getByteBuffer("csd-0")
        if (bufferInfo != null) {
          val byteBufferList = VideoEncoderHelper.extractVpsSpsPpsFromH265(bufferInfo.duplicate())
          if (byteBufferList.size == 3) {
            val sps = byteBufferList[1]
            val pps = byteBufferList[2]
            val vps = byteBufferList[0]
            videoPacket.sendVideoInfo(sps, pps, vps)
            sendInfo = true
          }
        }
      }

      else -> {}
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
}