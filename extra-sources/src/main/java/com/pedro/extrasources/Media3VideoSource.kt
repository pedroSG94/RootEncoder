package com.pedro.extrasources

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.common.frame.MediaFrame
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.extrasources.extractor.Media3Extractor

@OptIn(UnstableApi::class)
class Media3VideoSource(
    private val context: Context,
    private val path: Uri
): VideoSource() {

    private var player: ExoPlayer? = null
    private var surface: Surface? = null

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        val mediaExtractor = Media3Extractor(context)
        try {
            mediaExtractor.initialize(context, path)
            mediaExtractor.selectTrack(MediaFrame.Type.VIDEO)
        } catch (e: Exception) {
            throw IllegalArgumentException("Video file track not found")
        }
        mediaExtractor.release()
        player = ExoPlayer.Builder(context, TracksRenderersFactory(context, MediaFrame.Type.VIDEO)).build().also { exoPlayer ->
            exoPlayer.setVideoSurface(surface)
            val mediaItem = MediaItem.fromUri(path)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }
        return true
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        surface = Surface(surfaceTexture)
        player?.play()
    }

    override fun stop() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    override fun release() {

    }

    override fun isRunning(): Boolean = player?.isPlaying == true

    override fun getOrientationConfig(): OrientationForced = OrientationForced.LANDSCAPE

    fun getPlayer() = player
}