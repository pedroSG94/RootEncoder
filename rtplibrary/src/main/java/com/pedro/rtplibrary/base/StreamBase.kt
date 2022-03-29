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
import com.pedro.rtplibrary.view.GlCameraInterface
import java.nio.ByteBuffer

/**
 * Created by pedro on 21/2/22.
 *
 * Class to stream using Camera and microphone.
 * Allow:
 * - Choose camera1 or camera2.
 * - Rotation on realtime.
 * - Add filters only for preview or only for stream.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
abstract class StreamBase(context: Context): GetVideoData, GetAacData, GetMicrophoneData {

  //video and audio encoders
  private val videoEncoder by lazy { VideoEncoder(this) }
  private val audioEncoder by lazy { AudioEncoder(this) }
  //video render
  private val glInterface = GlCameraInterface(context)
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

  fun startStream(endPoint: String) {
    isStreaming = true
    rtpStartStream(endPoint)
    if (!recordController.isRunning) startSources()
    else videoEncoder.reset()
  }

  /**
   * @return True if encoders prepared successfully with previous parameters. False other way
   * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
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

  fun startRecord(path: String, listener: RecordController.Listener) {
    recordController.startRecord(path, listener)
    if (!isStreaming) startSources()
    else videoEncoder.reset()
  }

  /**
   * @return True if encoders prepared successfully with previous parameters. False other way
   * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
   */
  fun stopRecord(): Boolean {
    recordController.stopRecord()
    if (!isStreaming) {
      stopSources()
      return prepareEncoders()
    }
    return true
  }

  fun startPreview(textureView: TextureView) {
    startPreview(Surface(textureView.surfaceTexture))
    glInterface.setPreviewResolution(textureView.width, textureView.height)
  }

  fun startPreview(surfaceView: SurfaceView) {
    startPreview(surfaceView.holder.surface)
    glInterface.setPreviewResolution(surfaceView.width, surfaceView.height)
  }

  fun startPreview(surfaceTexture: SurfaceTexture) {
    startPreview(Surface(surfaceTexture))
  }

  fun startPreview(surface: Surface) {
    isOnPreview = true
    if (!glInterface.running) glInterface.start()
    if (!videoManager.isRunning()) {
      videoManager.start(glInterface.getSurfaceTexture())
    }
    glInterface.attachPreview(surface)
  }

  fun stopPreview() {
    isOnPreview = false
    if (!isStreaming && !recordController.isRunning) videoManager.stop()
    glInterface.deAttachPreview()
    if (!isStreaming && !recordController.isRunning) glInterface.stop()
  }

  fun changeVideoSourceCamera(source: VideoManager.Source) {
    videoManager.changeSourceCamera(source)
  }

  fun changeVideoSourceScreen(mediaProjection: MediaProjection) {
    videoManager.changeSourceScreen(mediaProjection)
  }

  fun changeAudioSourceMicrophone() {
    audioManager.changeSourceMicrophone()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun changeAudioSourceInternal(mediaProjection: MediaProjection) {
    audioManager.changeSourceInternal(mediaProjection)
  }

  fun switchVideoCamera() {
    videoManager.switchCamera()
  }

  fun getFacing(): CameraHelper.Facing = videoManager.getFacing()

  fun setOrientation(orientation: Int) {
    glInterface.setCameraOrientation(orientation)
  }

  fun getGlInterface(): GlCameraInterface = glInterface

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