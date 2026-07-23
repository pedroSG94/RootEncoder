package com.pedro.extrasources

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
import androidx.media3.common.util.UnstableApi
import com.pedro.common.toByteArray
import java.nio.ByteBuffer

@UnstableApi
class AudioBufferProcessor(
    private val callback: (ByteArray) -> Unit
) : AudioProcessor {

    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == AudioFormat.ENCODING_PCM_16BIT) inputAudioFormat
        else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        callback(inputBuffer.toByteArray())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        return EMPTY_BUFFER
    }

    override fun isEnded(): Boolean = inputEnded

    override fun flush() {
        inputEnded = false
    }

    override fun reset() {
        inputEnded = false
    }
}
