package com.pedro.library.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateManager
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaFrameInfo
import com.pedro.common.toUInt24
import com.pedro.common.toUInt32
import com.pedro.common.trySend
import com.pedro.encoder.video.VideoEncoderHelper
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import com.pedro.rtmp.amf.v0.AmfEcmaArray
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AudioFormat
import com.pedro.rtmp.flv.audio.packet.AacPacket
import com.pedro.rtmp.flv.audio.packet.G711Packet
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.packet.Av1Packet
import com.pedro.rtmp.flv.video.packet.H264Packet
import com.pedro.rtmp.flv.video.packet.H265Packet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class FlvMuxerRecordController: BaseRecordController() {

    private var outputStream: OutputStream? = null
    private var videoPacket: BasePacket = H264Packet()
    private var audioPacket: BasePacket = AacPacket()
    private val queue = LinkedBlockingQueue<MediaFrame>(200)
    private var job: Job? = null
    //metadata config
    private var width = 0
    private var height = 0
    private var fps = 0
    private var sampleRate = 0
    private var isStereo = true

    override fun startRecord(path: String, listener: RecordController.Listener?, tracks: RecordTracks) {
        this.tracks = tracks
        outputStream = FileOutputStream(path)
        start(listener)
    }

    override fun startRecord(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordTracks) {
        this.tracks = tracks
        outputStream = FileOutputStream(fd)
        start(listener)
    }

    private fun start(listener: RecordController.Listener?) {
        if (audioCodec == AudioCodec.OPUS) throw IOException("Unsupported AudioCodec: " + audioCodec.name)
        when (audioCodec) {
            AudioCodec.G711 -> audioPacket = G711Packet()
            AudioCodec.AAC -> audioPacket = AacPacket().apply {
                sendAudioInfo(sampleRate, isStereo)
            }
            else -> {}
        }
        videoPacket = when (videoCodec) {
            VideoCodec.H264 -> H264Packet()
            VideoCodec.H265 -> H265Packet()
            VideoCodec.AV1 -> Av1Packet()
        }
        this.listener = listener
        status = RecordController.Status.STARTED
        if (listener != null) {
            bitrateManager = BitrateManager(listener)
            listener.onStatusChange(status)
        } else {
            bitrateManager = null
        }
        queue.clear()
        outputStream?.let {
            try {
                it.write(createFlvFileHeader())
                writeFlvFileMetadata(it)
            } catch (_: Exception) {}
        }
        if (tracks == RecordTracks.AUDIO) status = RecordController.Status.RECORDING
        listener?.onStatusChange(status)
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val mediaFrame = runInterruptible { queue.take() }
                when (mediaFrame.type) {
                    MediaFrame.Type.VIDEO -> {
                        videoPacket.createFlvPacket(mediaFrame) { packet ->
                            outputStream?.let { writeFlvPacket(it, packet) }
                        }
                    }
                    MediaFrame.Type.AUDIO -> {
                        audioPacket.createFlvPacket(mediaFrame) { packet ->
                            outputStream?.let { writeFlvPacket(it, packet) }
                        }
                    }
                }
            }
        }
    }

    override fun stopRecord() {
        status = RecordController.Status.STOPPED
        runBlocking { job?.cancelAndJoin() }
        pauseMoment = 0
        pauseTime = 0
        startTs = 0
        queue.clear()
        videoPacket.reset(false)
        audioPacket.reset(false)
        try {
            outputStream?.close()
        } catch (_: Exception) { } finally {
            outputStream = null
        }
        requestKeyFrame = null
        if (listener != null) listener.onStatusChange(status)
    }

    override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
        getVideoInfo(videoBuffer, videoInfo)
        if (status == RecordController.Status.RECORDING && tracks != RecordTracks.AUDIO) {
            val frame = MediaFrame(videoBuffer.clone(), videoInfo.toMediaFrameInfo(), MediaFrame.Type.VIDEO)
            queue.trySend(frame)
        }
    }

    override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
        if (status == RecordController.Status.RECORDING && tracks != RecordTracks.VIDEO) {
            val frame = MediaFrame(audioBuffer.clone(), audioInfo.toMediaFrameInfo(), MediaFrame.Type.AUDIO)
            queue.trySend(frame)
        }
    }

    private var sendInfo = false

    private fun getVideoInfo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || isKeyFrame(buffer)) {
            if (!sendInfo && videoCodec == VideoCodec.H264) {
                Log.i(TAG, "formatChanged not called, doing manual sps/pps extraction...")
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
            } else if (!sendInfo && videoCodec == VideoCodec.H265) {
                Log.i(TAG, "formatChanged not called, doing manual vps/sps/pps extraction...")
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
            } else if (!sendInfo && videoCodec == VideoCodec.AV1) {
                Log.i(TAG, "formatChanged not called, doing manual av1 extraction...")
                val obuSequence = VideoEncoderHelper.extractObuSequence(buffer.duplicate(), info)
                if (obuSequence != null) {
                    (videoPacket as Av1Packet).sendVideoInfo(obuSequence)
                    sendInfo = true
                } else {
                    Log.e(TAG, "manual av1 extraction failed")
                }
            }
            if (sendInfo && status == RecordController.Status.STARTED) {
                requestKeyFrame = null
                status = RecordController.Status.RECORDING
                listener?.onStatusChange(status)
            }
        } else if (requestKeyFrame != null) {
            requestKeyFrame.onRequestKeyFrame()
            requestKeyFrame = null
        }
    }

    override fun setVideoFormat(videoFormat: MediaFormat) {
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val fps = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        this.width = width
        this.height = height
        this.fps = fps
        when (videoCodec) {
            VideoCodec.H264 -> {
                val sps = videoFormat.getByteBuffer("csd-0")
                val pps = videoFormat.getByteBuffer("csd-1")
                if (sps != null && pps != null) {
                    (videoPacket as H264Packet).sendVideoInfo(sps, pps)
                    sendInfo = true
                }
            }
            VideoCodec.H265 -> {
               val bufferInfo = videoFormat.getByteBuffer("csd-0")
               if (bufferInfo != null) {
                   val byteBufferList = VideoEncoderHelper.extractVpsSpsPpsFromH265(bufferInfo)
                   if (byteBufferList.size == 3) {
                       val sps = byteBufferList[1]
                       val pps = byteBufferList[2]
                       val vps = byteBufferList[0]
                       (videoPacket as H265Packet).sendVideoInfo(sps, pps, vps)
                       sendInfo = true
                   }
               }
           }
            VideoCodec.AV1 -> {
                val bufferInfo = videoFormat.getByteBuffer("csd-0")
                if (bufferInfo != null && bufferInfo.remaining() > 4) {
                    (videoPacket as Av1Packet).sendVideoInfo(bufferInfo)
                    sendInfo = true
                }
            }
        }
        if (sendInfo && status == RecordController.Status.STARTED) {
            requestKeyFrame = null
            status = RecordController.Status.RECORDING
            listener?.onStatusChange(status)
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
        val videoCodecValue = when (videoCodec) {
            VideoCodec.H264 -> VideoFormat.AVC.value
            VideoCodec.H265 -> VideoFormat.HEVC.value
            VideoCodec.AV1 -> VideoFormat.AV1.value
            else -> throw IllegalArgumentException("unsupported null codec")
        }
        info.setProperty("videocodecid", videoCodecValue.toDouble())
        info.setProperty("framerate", fps.toDouble())
        val audioCodecValue = when (audioCodec) {
            AudioCodec.AAC -> AudioFormat.AAC.value
            AudioCodec.G711 -> AudioFormat.G711_A.value
            else -> throw IllegalArgumentException("unsupported null codec")
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