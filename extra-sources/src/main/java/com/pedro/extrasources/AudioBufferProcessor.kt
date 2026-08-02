package com.pedro.extrasources

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Capture the decoded PCM and forward it to the callback.
 * The data is also written to the output buffer to keep the AudioSink writing to the AudioTrack.
 * That write is what paces the player to real time, so it must not be skipped even if the
 * player is muted.
 */
@UnstableApi
class AudioBufferProcessor(
    private val callback: (ByteArray) -> Unit
) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size <= 0) return
        val bytes = ByteArray(size)
        //get consume the input buffer, required to indicate that the data was processed
        inputBuffer.get(bytes)
        callback(bytes)
        replaceOutputBuffer(size).put(bytes).flip()
    }
}
