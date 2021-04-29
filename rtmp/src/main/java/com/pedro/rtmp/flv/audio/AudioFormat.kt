package com.pedro.rtmp.flv.audio

/**
 * Created by pedro on 29/04/21.
 * list of audio codec supported by FLV
 */
enum class AudioFormat(val mark: Byte) {
  PCM(0x00), ADPCM(0x01), MP3(0x02), PCM_LE(0x03), NELLYMOSER_16K(0x04),
  NELLYMOSER_8K(0x05), NELLYMOSER(0x06), G711_A(0x07), G711_MU(0x08), RESERVED(0X09),
  AAC(0xA), SPEEX(0xB), UNUSED(0xC), UNUSED_2(0xD), MP3_8K(0xE), DEVICE_SPECIFIC(0xF)
}