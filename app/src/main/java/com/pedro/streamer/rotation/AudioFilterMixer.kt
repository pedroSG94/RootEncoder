package com.pedro.streamer.rotation

import android.content.Context
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import com.pedro.common.TimeUtils
import com.pedro.common.frame.MediaFrame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.input.audio.AudioUtils
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.decoder.Extractor
import com.pedro.encoder.utils.PCMUtil
import java.io.FileDescriptor

class AudioFilterMixer(
  private val extractor: Extractor
): CustomAudioEffect() {

  @Volatile
  private var running = false
  private val audioUtils = AudioUtils()
  private var originalVolume = 1f
  private var mixedVolume = 1f
  private var buffer = ByteArray(AudioEncoder.inputSize)
  private var codec: MediaCodec? = null
  private var channels = 1
  private val bufferInfo = MediaCodec.BufferInfo()
  private var startTs = 0L

  fun start(filePath: String) {
    extractor.initialize(filePath)
    start()
  }

  fun start(context: Context, uri: Uri) {
    extractor.initialize(context, uri)
    start()
  }

  fun start(fileDescriptor: FileDescriptor) {
    extractor.initialize(fileDescriptor)
    start()
  }

  private fun start() {
    running = true
    val mime = extractor.selectTrack(MediaFrame.Type.AUDIO)
    val audioInfo = extractor.getAudioInfo()
    val format = extractor.getFormat()
    channels = audioInfo.channels
    codec = MediaCodec.createDecoderByType(mime)
    codec?.configure(format, null, null, 0)
    codec?.start()
  }

  fun stop() {
    running = false
    runCatching { codec?.flush() }
    runCatching {
      codec?.stop()
      codec?.release()
    }
    runCatching { extractor.release() }
    codec = null
    startTs = 0
  }

  private fun read(): ByteArray {
    if (startTs == 0L) startTs = TimeUtils.getCurrentTimeMicro()
    if (!running) return byteArrayOf()
    runCatching {
      val inIndex = codec?.dequeueInputBuffer(10000) ?: -1
      if (inIndex < 0) return byteArrayOf()

      val input = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        codec?.getInputBuffer(inIndex)
      } else {
        codec?.getInputBuffers()[inIndex]
      }
      if (input == null) return byteArrayOf()
      val sampleSize = extractor.readFrame(input)
      val finished = !extractor.advance()
      if (finished) running = false
      codec?.queueInputBuffer(inIndex, 0, sampleSize, TimeUtils.getCurrentTimeMicro() - startTs, 0)

      val outIndex = codec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
      if (outIndex < 0) return byteArrayOf()

      val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        codec?.getOutputBuffer(outIndex)
      } else {
        codec?.getOutputBuffers()[outIndex]
      }
      if (output == null) return byteArrayOf()
      val data = ByteArray(output.remaining())
      output.get(data)
      val bufferResult = if (channels > 2) PCMUtil.pcmToStereo(data, channels) else data
      codec?.releaseOutputBuffer(outIndex, false)
      return bufferResult
    }.getOrElse {
      return byteArrayOf()
    }
  }

  override fun process(pcmBuffer: ByteArray): ByteArray {
    var mixedBuffer = read()
    if (mixedBuffer.isEmpty()) return pcmBuffer
    while (mixedBuffer.size < pcmBuffer.size) {
      val nextMixedBuffer = read()
      if (nextMixedBuffer.isEmpty()) return pcmBuffer
      mixedBuffer = mixedBuffer.plus(nextMixedBuffer)
    }
    audioUtils.applyVolumeAndMix(pcmBuffer, originalVolume, mixedBuffer, mixedVolume, buffer)
    return buffer
  }
}