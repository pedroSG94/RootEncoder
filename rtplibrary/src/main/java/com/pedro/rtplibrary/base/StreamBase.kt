package com.pedro.rtplibrary.base

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.util.sources.AudioManager
import com.pedro.rtplibrary.util.sources.VideoManager
import com.pedro.rtplibrary.util.RecordController
import com.pedro.rtplibrary.view.GlStreamInterface
import java.nio.ByteBuffer

/**
 * Created by pedro on 21/2/22.
 *
 * Allow:
 * - video source camera1, camera2 or screen.
 * - audio source microphone or internal.
 * - Rotation on realtime.
 * - Add filters only for preview or only for stream.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
abstract class StreamBase(context: Context): GetVideoData, GetAacData, GetMicrophoneData {

  //video and audio encoders
  private val videoEncoder by lazy { VideoEncoder(this) }
  private val audioEncoder by lazy { AudioEncoder(this) }
  //video render
  private val glInterface = GlStreamInterface(context)
  //video and audio sources
  private val videoManager = VideoManager(context)
  private val audioManager by lazy { AudioManager(this) }
  //video/audio record
  private val recordController = RecordController()
  var isStreaming = false
    private set
  var isOnPreview = false
    private set
  val isRecording: Boolean
    get() = recordController.isRunning

  init {
    glInterface.init()
  }

  /**
   * Necessary only one time before start preview, stream or record.
   * If you want change values stop preview, stream and record is necessary.
   *
   * @return True if success, False if failed
   */
  @JvmOverloads
  fun prepareVideo(width: Int, height: Int, bitrate: Int, fps: Int = 30, iFrameInterval: Int = 2): Boolean {
    val videoResult = videoManager.createVideoManager(width, height, fps)
    if (videoResult) {
      glInterface.setEncoderSize(width, height)
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, 0,
        iFrameInterval, FormatVideoEncoder.SURFACE)
    }
    return videoResult
  }

  /**
   * Necessary only one time before start stream or record.
   * If you want change values stop stream and record is necessary.
   *
   * @return True if success, False if failed
   */
  @JvmOverloads
  fun prepareAudio(sampleRate: Int, isStereo: Boolean, bitrate: Int, echoCanceler: Boolean = false,
    noiseSuppressor: Boolean = false): Boolean {
    val audioResult = audioManager.createAudioManager(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    if (audioResult) {
      return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo, audioManager.getMaxInputSize())
    }
    return audioResult
  }

  /**
   * Start stream.
   *
   * Must be called after prepareVideo and prepareAudio
   */
  fun startStream(endPoint: String) {
    isStreaming = true
    rtpStartStream(endPoint)
    if (!recordController.isRunning) startSources()
    else videoEncoder.requestKeyframe()
  }

  /**
   * Stop stream.
   *
   * @return True if encoders prepared successfully with previous parameters. False other way
   * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
   *
   * Must be called after prepareVideo and prepareAudio.
   */
  fun stopStream(): Boolean {
    isStreaming = false
    rtpStopStream()
    if (!recordController.isRunning) {
      stopSources()
      return prepareEncoders()
    }
    return true
  }

  /**
   * Start record.
   *
   * Must be called after prepareVideo and prepareAudio.
   */
  fun startRecord(path: String, listener: RecordController.Listener) {
    recordController.startRecord(path, listener)
    if (!isStreaming) startSources()
    else videoEncoder.requestKeyframe()
  }

  /**
   * @return True if encoders prepared successfully with previous parameters. False other way
   * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
   *
   * Must be called after prepareVideo and prepareAudio.
   */
  fun stopRecord(): Boolean {
    recordController.stopRecord()
    if (!isStreaming) {
      stopSources()
      return prepareEncoders()
    }
    return true
  }

  /**
   * Start preview in the selected TextureView.
   * Must be called after prepareVideo.
   */
  fun startPreview(textureView: TextureView) {
    startPreview(Surface(textureView.surfaceTexture))
    glInterface.setPreviewResolution(textureView.width, textureView.height)
  }

  /**
   * Start preview in the selected SurfaceView.
   * Must be called after prepareVideo.
   */
  fun startPreview(surfaceView: SurfaceView) {
    startPreview(surfaceView.holder.surface)
    glInterface.setPreviewResolution(surfaceView.width, surfaceView.height)
  }

  /**
   * Start preview in the selected SurfaceTexture.
   * Must be called after prepareVideo.
   */
  fun startPreview(surfaceTexture: SurfaceTexture) {
    startPreview(Surface(surfaceTexture))
  }

  /**
   * Start preview in the selected Surface.
   * Must be called after prepareVideo.
   */
  fun startPreview(surface: Surface) {
    isOnPreview = true
    if (!glInterface.running) glInterface.start()
    if (!videoManager.isRunning()) {
      videoManager.start(glInterface.getSurfaceTexture())
    }
    glInterface.attachPreview(surface)
  }

  /**
   * Stop preview.
   * Must be called after prepareVideo.
   */
  fun stopPreview() {
    isOnPreview = false
    if (!isStreaming && !recordController.isRunning) videoManager.stop()
    glInterface.deAttachPreview()
    if (!isStreaming && !recordController.isRunning) glInterface.stop()
  }

  /**
   * Change video source to Camera1 or Camera2.
   * Must be called after prepareVideo.
   */
  fun changeVideoSourceCamera(source: VideoManager.Source) {
    glInterface.setForceRender(false)
    videoManager.changeSourceCamera(source)
  }

  /**
   * Change video source to Screen.
   * Must be called after prepareVideo.
   */
  fun changeVideoSourceScreen(mediaProjection: MediaProjection) {
    glInterface.setForceRender(true)
    videoManager.changeSourceScreen(mediaProjection)
  }

  /**
   * Disable video stopping process video frames from video source.
   * You can return to camera/screen video using changeVideoSourceCamera/changeVideoSourceScreen
   *
   * @NOTE:
   * This isn't recommended because it isn't supported in all servers/players.
   * Use BlackFilterRender to send only black images is recommended.
   */
  fun changeVideoSourceDisabled() {
    glInterface.setForceRender(false)
    videoManager.changeVideoSourceDisabled()
  }

  /**
   * Change audio source to Microphone.
   * Must be called after prepareAudio.
   */
  fun changeAudioSourceMicrophone() {
    audioManager.changeSourceMicrophone()
  }

  /**
   * Change audio source to Internal.
   * Must be called after prepareAudio.
   */
  @RequiresApi(Build.VERSION_CODES.Q)
  fun changeAudioSourceInternal(mediaProjection: MediaProjection) {
    audioManager.changeSourceInternal(mediaProjection)
  }

  /**
   * Disable audio stopping process audio frames from audio source.
   * You can return to microphone/internal audio using changeAudioSourceMicrophone/changeAudioSourceInternal
   *
   * @NOTE:
   * This isn't recommended because it isn't supported in all servers/players.
   * Use mute and unMute to send empty audio is recommended
   */
  fun changeAudioSourceDisabled() {
    audioManager.changeAudioSourceDisabled()
  }

  /**
   * Mute microphone or internal audio.
   * Must be called after prepareAudio.
   */
  fun mute() {
    audioManager.mute()
  }

  /**
   * Mute microphone or internal audio.
   * Must be called after prepareAudio.
   */
  fun unMute() {
    audioManager.unMute()
  }

  /**
   * Check if microphone or internal audio is muted.
   * Must be called after prepareAudio.
   */
  fun isMuted(): Boolean = audioManager.isMuted()

  /**
   * Switch between front or back camera if using Camera1 or Camera2.
   * Must be called after prepareVideo.
   */
  fun switchCamera() {
    videoManager.switchCamera()
  }

  /**
   * get if using front or back camera with Camera1 or Camera2.
   * Must be called after prepareVideo.
   */
  fun getCameraFacing(): CameraHelper.Facing = videoManager.getCameraFacing()

  /**
   * Change stream orientation depend of activity orientation.
   * This method affect ro preview and stream.
   * Must be called after prepareVideo.
   */
  fun setOrientation(orientation: Int) {
    glInterface.setCameraOrientation(orientation)
  }

  /**
   * Get glInterface used to render video.
   * This is useful to send filters to stream.
   * Must be called after prepareVideo.
   */
  fun getGlInterface(): GlStreamInterface = glInterface

  private fun startSources() {
    if (!glInterface.running) glInterface.start()
    if (!videoManager.isRunning()) {
      videoManager.start(glInterface.getSurfaceTexture())
    }
    audioManager.start()
    videoEncoder.start()
    audioEncoder.start()
    glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
  }

  private fun stopSources() {
    if (!isOnPreview) videoManager.stop()
    audioManager.stop()
    videoEncoder.stop()
    audioEncoder.stop()
    glInterface.removeMediaCodecSurface()
    if (!isOnPreview) glInterface.stop()
  }

  private fun prepareEncoders(): Boolean {
    return videoEncoder.prepareVideoEncoder() && audioEncoder.prepareAudioEncoder()
  }

  override fun inputPCMData(frame: Frame) {
    audioEncoder.inputPCMData(frame)
  }

  override fun onVideoFormat(mediaFormat: MediaFormat) {
    recordController.setVideoFormat(mediaFormat)
  }

  override fun onAudioFormat(mediaFormat: MediaFormat) {
    recordController.setAudioFormat(mediaFormat)
  }

  override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    onSpsPpsVpsRtp(sps.duplicate(), pps.duplicate(), vps?.duplicate())
  }

  override fun getVideoData(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    getH264DataRtp(h264Buffer, info)
    recordController.recordVideo(h264Buffer, info)
  }

  override fun getAacData(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    getAacDataRtp(aacBuffer, info)
    recordController.recordAudio(aacBuffer, info)
  }

  protected abstract fun videoInfo(width: Int, height: Int)
  protected abstract fun audioInfo(sampleRate: Int, isStereo: Boolean)
  protected abstract fun rtpStartStream(endPoint: String)
  protected abstract fun rtpStopStream()
  protected abstract fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
  protected abstract fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
  protected abstract fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
}