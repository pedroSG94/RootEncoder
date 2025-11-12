package com.pedro.encoder.input.decoder

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.view.Surface
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.encoder.utils.CodecUtil
import java.nio.ByteBuffer

class BufferDecoder(videoCodec: VideoCodec) {

    private var codec: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private var startTs = 0L
    private val bufferInfo = BufferInfo()
    private val mime = when (videoCodec){
        VideoCodec.H264 -> CodecUtil.H264_MIME
        VideoCodec.H265 -> CodecUtil.H265_MIME
        VideoCodec.AV1 -> CodecUtil.AV1_MIME
    }
    private var isSurfaceMode = false

    fun prepare(width: Int, height: Int, fps: Int, rotation: Int) {
        val mediaFormat = MediaFormat()
        if (rotation == 0 || rotation == 180) {
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, width)
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height)
        } else {
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, height)
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, width)
        }
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        mediaFormat.setString(MediaFormat.KEY_MIME, mime)
        this.mediaFormat = mediaFormat
    }

    /**
     * @return output raw color. Value indicated in MediaCodecInfo.CodecCapabilities. -1 if not found
     */
    fun start(surface: Surface?): Int {
        isSurfaceMode = surface != null
        mediaFormat?.let {
            startTs = TimeUtils.getCurrentTimeMicro()
            codec = MediaCodec.createDecoderByType(mime)
            codec?.configure(mediaFormat, surface, null, 0)
            val color = try {
                codec?.outputFormat?.getInteger(MediaFormat.KEY_COLOR_FORMAT) ?: -1
            } catch (e: Exception) { -1 }
            codec?.start()
            return color
        }
        return -1
    }

    fun stop() {
        try {
            codec?.flush()
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) { } finally {
            codec = null
            mediaFormat = null
            startTs = 0
            isSurfaceMode = false
        }
    }

    /**
     * @return The frame data in raw format indicated in start method. return null if fail or in surface mode
     */
    fun decode(data: ByteArray): ByteBuffer? {
        codec?.let {
            val inIndex = it.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val input = it.getInputBuffer(inIndex)
                input?.put(data)
                it.queueInputBuffer(inIndex, 0, data.size, TimeUtils.getCurrentTimeMicro() - startTs, 0)
            }
            val outIndex = it.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                val rawData = if (!isSurfaceMode) it.getOutputBuffer(outIndex)?.clone() else null
                it.releaseOutputBuffer(outIndex, isSurfaceMode)
                return rawData
            }
        }
        return null
    }
}