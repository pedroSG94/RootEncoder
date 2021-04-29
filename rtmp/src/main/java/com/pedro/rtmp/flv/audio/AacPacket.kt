package com.pedro.rtmp.flv.audio

import android.media.MediaCodec
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import java.nio.ByteBuffer
import kotlin.experimental.or

/**
 * Created by pedro on 8/04/21.
 */
class AacPacket(private val audioPacketCallback: AudioPacketCallback) {

  private val header = ByteArray(2)
  //first time we need send audio config
  private var configSend = false

  private var sampleRate = 44100
  private var isStereo = true
  //In microphone we are using always 16bits pcm encoding. Change me if needed
  private var audioSize = AudioSize.SND_16_BIT

  enum class Type(val mark: Byte) {
    SEQUENCE(0x00), RAW(0x01)
  }

  fun sendAudioInfo(sampleRate: Int, isStereo: Boolean, audioSize: AudioSize = AudioSize.SND_16_BIT) {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
    this.audioSize = audioSize
  }

  fun createFlvAudioPacket(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    //header is 2 bytes length
    //4 bits sound format, 2 bits sound rate, 1 bit sound size, 1 bit sound type
    //8 bits sound data (always 10 because we aer using aac)
    header[0] = if (isStereo) AudioSoundType.STEREO.mark else AudioSoundType.MONO.mark
    header[0] = header[0] or (audioSize.mark.toInt() shl 1).toByte()
    val soundRate = when (sampleRate) {
      44100 -> AudioSoundRate.SR_44_1K
      22050 -> AudioSoundRate.SR_22K
      11025 -> AudioSoundRate.SR_11K
      5500 -> AudioSoundRate.SR_5_5K
      else -> AudioSoundRate.SR_44_1K
    }
    header[0] = header[0] or (soundRate.ordinal shl 2).toByte()
    header[0] = header[0] or (AudioFormat.AAC.ordinal shl 4).toByte()
    val buffer: ByteArray
    if (!configSend) {
      buffer = ByteArray(9 + header.size)
      header[1] = Type.SEQUENCE.mark
      //try get audio object type, if not possible set AAC_LC
      val objectType = if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) byteBuffer.get(0).toInt() and 0xF8 else AudioObjectType.AAC_LC.ordinal
      val config = AudioSpecificConfig(objectType, sampleRate, if (isStereo) 2 else 1)
      config.write(buffer)
    } else {
      header[1] = Type.RAW.mark
      buffer = ByteArray(info.size - info.offset + header.size)

      byteBuffer.get(buffer, header.size, info.size - info.offset)
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    val ts = info.presentationTimeUs / 1000
    audioPacketCallback.onAudioFrameCreated(FlvPacket(buffer, ts, buffer.size, FlvType.AUDIO))
  }

  fun reset() {
    configSend = false
  }
}