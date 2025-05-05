package com.pedro.extrasources

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.common.TimeUtils
import com.pedro.common.frame.MediaFrame
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.extrasources.extractor.Media3Extractor

@OptIn(UnstableApi::class)
class Media3AudioSource(
    private val context: Context,
    private val path: Uri
): AudioSource() {

    private var player: ExoPlayer? = null

    private val processor = AudioBufferProcessor { bytes ->
        val frame = Frame(bytes, 0, bytes.size, TimeUtils.getCurrentTimeMicro())
        getMicrophoneData?.inputPCMData(frame)
    }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        val mediaExtractor = Media3Extractor(context)
        try {
            mediaExtractor.initialize(context, path)
            mediaExtractor.selectTrack(MediaFrame.Type.AUDIO)
        } catch (e: Exception) {
            throw IllegalArgumentException("Audio file track not found")
        }
        val audioInfo = mediaExtractor.getAudioInfo()
        if (audioInfo.sampleRate != sampleRate) {
            throw IllegalArgumentException("Audio file sample rate (${audioInfo.sampleRate}) is different than the configured: $sampleRate")
        }
        if (audioInfo.channels > 1 != isStereo) {
            throw IllegalArgumentException("Audio file isStereo (${audioInfo.channels > 1}) is different than the configured: $isStereo")
        }
        mediaExtractor.release()
        player = ExoPlayer.Builder(context, TracksRenderersFactory(context, MediaFrame.Type.AUDIO, processor)).build().also { exoPlayer ->
            val mediaItem = MediaItem.fromUri(path)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }
        return true
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        player?.play()
    }

    override fun stop() {
        getMicrophoneData = null
        player?.release()
        player = null
    }

    override fun release() {

    }

    override fun isRunning(): Boolean = player?.isPlaying == true

    fun getPlayer() = player
}