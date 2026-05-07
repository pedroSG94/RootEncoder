package com.pedro.streamer.utils

import com.pedro.encoder.input.audio.AudioUtils
import com.pedro.encoder.input.audio.CustomAudioEffect
import java.io.ByteArrayOutputStream

class AudioMixer : CustomAudioEffect() {
    private val audioUtils = AudioUtils()
    private val audioBufferStream = ByteArrayOutputStream()
    var audioVolume = 1.0f
    var micVolume = 1.0f

    @Synchronized
    override fun process(pcmBuffer: ByteArray): ByteArray {
        val audioData = audioBufferStream.toByteArray()

        // If the microphone is muted, pcmBuffer will show only zeros.
        // We still generate results based on the size of the microphone.
        val mixedResult = ByteArray(pcmBuffer.size)

        if (audioData.size >= pcmBuffer.size) {
            // Extract Audio data
            val toMixAudio = audioData.copyOfRange(0, pcmBuffer.size)

            // MIXING: Use AudioUtils to blend Mic and Audio
            // MicVolume and MusicVolume will determine which one is louder.
            audioUtils.applyVolumeAndMix(
                buffer = pcmBuffer, volume = micVolume,
                buffer2 = toMixAudio, volume2 = audioVolume,
                dst = mixedResult
            )

            //Remove used music from library.
            val remaining = audioData.copyOfRange(pcmBuffer.size, audioData.size)
            audioBufferStream.reset()
            audioBufferStream.write(remaining)

            return mixedResult
        } else {
            // If there is no music, return the microphone sound (with mic volume applied).
            audioUtils.applyVolume(pcmBuffer, micVolume)
            return pcmBuffer
        }
    }

    @Synchronized
    fun pushAudioData(data: ByteArray) {
        // Limit the cache memory to avoid expensive RAM usage.
        if (audioBufferStream.size() > 128000) return
        audioBufferStream.write(data)
    }

    @Synchronized
    fun clear() {
        audioBufferStream.reset()
    }
}