package com.pedro.rtplibrary.base

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.MotionEvent
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
import com.pedro.rtplibrary.base.recording.BaseRecordController
import com.pedro.rtplibrary.base.recording.RecordController
import com.pedro.rtplibrary.util.AndroidMuxerRecordController
import com.pedro.rtplibrary.util.sources.AudioManager
import com.pedro.rtplibrary.util.sources.VideoManager
import com.pedro.rtplibrary.view.GlStreamInterface
import java.nio.ByteBuffer

/**
 * Created by pedro on 21/2/22.
 *
 * Allow:
 * - video source camera1, camera2 or screen.
 * - audio source microphone or internal.
 * - Rotation on realtime.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
abstract class StreamBase(context: Context, videoSource: VideoManager.Source,
  audioSource: AudioManager.Source): GetVideoData, GetAacData, GetMicrophoneData {

  //video and audio encoders
  private val videoEncoder by lazy { VideoEncoder(this) }
  private val audioEncoder by lazy { AudioEncoder(this) }
  //video render
  private val glInterface = GlStreamInterface(context)
  //video and audio sources
  private val videoManager = VideoManager(context, videoSource)
  private val audioManager by lazy { AudioManager(this, audioSource) }
  //video/audio record
  private var recordController: BaseRecordController = AndroidMuxerRecordController()
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
  fun prepareVideo(width: Int, height: Int, bitrate: Int, fps: Int = 30, iFrameInterval: Int = 2,
    avcProfile: Int = -1, avcProfileLevel: Int = -1): Boolean {
    val videoResult = videoManager.createVideoManager(width, height, fps)
    if (videoResult) {
      glInterface.setEncoderSize(width, height)
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, 0,
        iFrameInterval, FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel)
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
    if (!isRecording) startSources()
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
    if (!isRecording) {
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
    startPreview(Surface(textureView.surfaceTexture), textureView.width, textureView.height)
  }

  /**
   * Start preview in the selected SurfaceView.
   * Must be called after prepareVideo.
   */
  fun startPreview(surfaceView: SurfaceView) {
    startPreview(surfaceView.holder.surface, surfaceView.width, surfaceView.height)
  }

  /**
   * Start preview in the selected SurfaceTexture.
   * Must be called after prepareVideo.
   */
  fun startPreview(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
    startPreview(Surface(surfaceTexture), width, height)
  }

  /**
   * Start preview in the selected Surface.
   * Must be called after prepareVideo.
   */
  fun startPreview(surface: Surface, width: Int, height: Int) {
    if (!surface.isValid) throw IllegalArgumentException("Make sure the Surface is valid")
    isOnPreview = true
    if (!glInterface.running) glInterface.start()
    if (!videoManager.isRunning()) {
      videoManager.start(glInterface.getSurfaceTexture())
    }
    glInterface.attachPreview(surface)
    glInterface.setPreviewResolution(width, height)
  }

  /**
   * Stop preview.
   * Must be called after prepareVideo.
   */
  fun stopPreview() {
    isOnPreview = false
    if (!isStreaming && !isRecording) videoManager.stop()
    glInterface.deAttachPreview()
    if (!isStreaming && !isRecording) glInterface.stop()
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
   * Set a custom size of audio buffer input.
   * If you set 0 or less you can disable it to use library default value.
   * Must be called before of prepareAudio method.
   *
   * @param size in bytes. Recommended multiple of 1024 (2048, 4096, 8196, etc)
   */
  fun setAudioMaxInputSize(size: Int) {
    audioManager.setMaxInputSize(size)
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
   * Get camera resolutions.
   *
   * @param source select camera source (Camera1 or Camera2) of the required resolutions.
   * @param facing indicate if resolutions provide from front camera or back camera.
   */
  fun getCameraResolutions(source: VideoManager.Source, facing: CameraHelper.Facing): List<Size> {
    return videoManager.getCameraResolutions(source, facing)
  }

  /**
   * Set exposure to Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun setExposure(level: Int) {
    videoManager.setExposure(level)
  }

  /**
   * @return exposure of Camera1 or Camera2. 0 with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will return 0.
   */
  fun getExposure(): Int = videoManager.getExposure()

  /**
   * Enable lantern using Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun enableLantern() {
    videoManager.enableLantern()
  }

  /**
   * Disable lantern using Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun disableLantern() {
    videoManager.disableLantern()
  }

  /**
   * @return lantern state using Camera1 or Camera2. False with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will return false.
   */
  fun isLanternEnabled(): Boolean = videoManager.isLanternEnabled()

  /**
   * Enable auto focus using Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun enableAutoFocus() {
    videoManager.enableAutoFocus()
  }

  /**
   * Disable auto focus using Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun disableAutoFocus() {
    videoManager.disableAutoFocus()
  }

  /**
   * @return auto focus state using Camera1 or Camera2. False with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will return false.
   */
  fun isAutoFocusEnabled(): Boolean = videoManager.isAutoFocusEnabled()

  /**
   * Set zoom to Camera1 or Camera2. Ignored with other video source.
   * This method is used with onTouch event to implement zoom with gestures.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun setZoom(event: MotionEvent) {
    videoManager.setZoom(event)
  }

  /**
   * Set zoom to Camera1 or Camera2. Ignored with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will be ignored.
   */
  fun setZoom(level: Float) {
    videoManager.setZoom(level)
  }

  /**
   * @return zoom range (min and max) using Camera1 or Camera2. Range(0f, 0f) with other video source
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will return Range(0f, 0f).
   */
  fun getZoomRange(): Range<Float> = videoManager.getZoomRange()

  /**
   * @return current zoom using Camera1 or Camera2. 0f with other video source.
   *
   * Must be called with isOnPreview, isStreaming or isRecording true or will return 0f.
   */
  fun getZoom(): Float = videoManager.getZoom()

  /**
   * Change stream orientation depend of activity orientation.
   * This method affect ro preview and stream.
   * Must be called after prepareVideo.
   */
  fun setOrientation(orientation: Int) {
    glInterface.setCameraOrientation(orientation)
  }

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  @JvmOverloads
  fun reTry(delay: Long, reason: String, backupUrl: String? = null): Boolean {
    val result = shouldRetry(reason)
    if (result) {
      videoEncoder.requestKeyframe()
      reConnect(delay, backupUrl)
    }
    return result
  }

  /**
   * Get glInterface used to render video.
   * This is useful to send filters to stream.
   * Must be called after prepareVideo.
   */
  fun getGlInterface(): GlStreamInterface = glInterface

  fun setRecordController(recordController: BaseRecordController) {
    if (!isRecording) this.recordController = recordController
  }

  protected fun setVideoMime(videoMime: String) {
    recordController.setVideoMime(videoMime)
    videoEncoder.type = videoMime
  }

  protected fun getVideoResolution() = Size(videoEncoder.width, videoEncoder.height)

  protected fun getVideoFps() = videoEncoder.fps

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
    if (!isRecording) recordController.resetFormats()
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

  protected abstract fun audioInfo(sampleRate: Int, isStereo: Boolean)
  protected abstract fun rtpStartStream(endPoint: String)
  protected abstract fun rtpStopStream()
  protected abstract fun setAuthorization(user: String?, password: String?)
  protected abstract fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
  protected abstract fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
  protected abstract fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
  protected abstract fun shouldRetry(reason: String): Boolean
  protected abstract fun reConnect(delay: Long, backupUrl: String?)
  abstract fun setReTries(reTries: Int)
  abstract fun hasCongestion(): Boolean
  abstract fun setLogs(enabled: Boolean)
  abstract fun setCheckServerAlive(enabled: Boolean)
  @Throws(RuntimeException::class)
  abstract fun resizeCache(newSize: Int)
  abstract fun getCacheSize(): Int
  abstract fun getSentAudioFrames(): Long
  abstract fun getSentVideoFrames(): Long
  abstract fun getDroppedAudioFrames(): Long
  abstract fun getDroppedVideoFrames(): Long
  abstract fun resetSentAudioFrames()
  abstract fun resetSentVideoFrames()
  abstract fun resetDroppedAudioFrames()
  abstract fun resetDroppedVideoFrames()
}