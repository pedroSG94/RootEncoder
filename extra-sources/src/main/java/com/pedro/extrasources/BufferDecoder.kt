package com.pedro.extrasources

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.view.Surface
import com.pedro.common.TimeUtils
import com.pedro.encoder.utils.CodecUtil

class BufferDecoder {

    private var codec: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private var startTs = 0L
    private val bufferInfo = BufferInfo()

    fun prepare(width: Int, height: Int, fps: Int, rotation: Int, mime: String) {
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

    fun start(surface: Surface) {
        mediaFormat?.let {
            startTs = TimeUtils.getCurrentTimeMicro()
            codec = MediaCodec.createDecoderByType(CodecUtil.H264_MIME)
            codec?.configure(mediaFormat, surface, null, 0)
            codec?.start()
        }
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
        }
    }

    fun decode(data: ByteArray) {
        codec?.let {
            val inIndex = it.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val input = it.getInputBuffer(inIndex)
                input?.put(data)
                it.queueInputBuffer(inIndex, 0, data.size, TimeUtils.getCurrentTimeMicro() - startTs, 0)
            }
            val outIndex = it.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                it.releaseOutputBuffer(outIndex, true)
            }
        }
    }
}