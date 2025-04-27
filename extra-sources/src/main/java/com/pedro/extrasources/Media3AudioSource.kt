package com.pedro.extrasources

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.common.TimeUtils
import com.pedro.common.frame.MediaFrame
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource

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

    fun moveTo(position: Long) {
        player?.seekTo(position)
    }
}