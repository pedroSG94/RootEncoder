package com.pedro.encoder.input.sources.video

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.toByteArray
import com.pedro.encoder.Frame
import com.pedro.encoder.input.decoder.BufferDecoder
import com.pedro.encoder.utils.yuv.NV21Utils
import com.pedro.encoder.utils.yuv.YUVUtil
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class BufferVideoSource(
    private val format: Format,
    private val bitrate: Int,
): VideoSource() {

    enum class Format {
        H264, H265, AV1, RGB, ARGB, NV21, NV12
    }

    private var running = false
    private val videoEncoder = VideoEncoder(object: GetVideoData {
        override fun onVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) { }

        override fun getVideoData(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            decoder.decode(videoBuffer.toByteArray())
        }

        override fun onVideoFormat(mediaFormat: MediaFormat) { }
    })
    private val decoder = BufferDecoder(when (format) {
        Format.H265 -> VideoCodec.H265
        Format.AV1 -> VideoCodec.AV1
        else -> VideoCodec.H264
    })
    private var scope = CoroutineScope(Dispatchers.IO)
    private val queue: BlockingQueue<Frame> = ArrayBlockingQueue(80)

    fun setBuffer(data: ByteBuffer) {
        setBuffer(data.toByteArray())
    }

    fun setBuffer(data: ByteArray) {
        if (!running) return
        when (format) {
            Format.RGB, Format.ARGB -> {
                throw IllegalStateException("Use setBuffer IntArray instead")
            }
            Format.NV21, Format.NV12 -> {
                queue.offer(Frame(data, 0, data.size, TimeUtils.getCurrentTimeMicro()))
            }
            else -> { scope.launch { decoder.decode(data) } }
        }
    }

    fun setBuffer(data: IntArray) {
        if (!running) return
        when (format) {
            Format.RGB, Format.ARGB -> {
                val yuv = YUVUtil.ARGBtoYUV420SemiPlanar(data, width, height)
                queue.offer(Frame(yuv, 0, yuv.size, TimeUtils.getCurrentTimeMicro()))
            }
            else -> {
                throw IllegalStateException("Method only supported with format RGB and ARGB")
            }
        }
    }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        val result = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, 2, FormatVideoEncoder.YUV420Dynamical)
        decoder.prepare(width, height, fps, rotation)
        NV21Utils.preAllocateBuffers(width * height * 3 / 2)
        return result
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        scope = CoroutineScope(Dispatchers.IO)
        queue.clear()
        running = true
        videoEncoder.start()
        decoder.start(Surface(surfaceTexture))
        when (format) {
            Format.RGB, Format.ARGB, Format.NV21, Format.NV12 -> {
                scope.launch {
                    while (running) {
                        val frame = queue.take()
                        videoEncoder.inputYUVData(frame)
                    }
                }
            }
            else -> {}
        }
    }

    override fun stop() {
        running = false
        scope.cancel()
        videoEncoder.stop()
        decoder.stop()
        queue.clear()
    }

    override fun release() { }

    override fun isRunning(): Boolean = running
}