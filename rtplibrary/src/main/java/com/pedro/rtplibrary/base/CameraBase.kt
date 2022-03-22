package com.pedro.rtplibrary.base

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.util.CameraManager
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
abstract class CameraBase(context: Context): GetVideoData, GetAacData, GetMicrophoneData {

  //video and audio encoders
  private val videoEncoder by lazy { VideoEncoder(this) }
  private val audioEncoder by lazy { AudioEncoder(this) }
  //video render
  private val glInterface = GlCameraInterface(context)
  //video and audio sources
  private val cameraManager = CameraManager(context, CameraManager.Source.CAMERA1)
  private val microphoneManager by lazy { MicrophoneManager(this) }
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

  @JvmOverloads
  fun prepareVideo(width: Int, height: Int, bitrate: Int, fps: Int = 30, iFrameInterval: Int = 2): Boolean {
    val cameraResult = cameraManager.createCameraManager(width, height, fps)
    if (cameraResult) {
      glInterface.setEncoderSize(width, height)
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, 0,
        iFrameInterval, FormatVideoEncoder.SURFACE)
    }
    return cameraResult
  }

  @JvmOverloads
  fun prepareAudio(sampleRate: Int, isStereo: Boolean, bitrate: Int, echoCanceler: Boolean = false,
    noiseSuppressor: Boolean = false): Boolean {
    val microphoneResult = microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    if (microphoneResult) {
      return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo, microphoneManager.maxInputSize)
    }
    return microphoneResult
  }

  fun startStream(endPoint: String) {
    isStreaming = true
    rtpStartStream(endPoint)
    if (!recordController.isRunning) {
      if (!glInterface.running) glInterface.start()
      if (!cameraManager.isRunning()) {
        cameraManager.start(glInterface.getSurfaceTexture())
      }
      microphoneManager.start()
      videoEncoder.start()
      audioEncoder.start()
      glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
    } else {
      videoEncoder.reset()
    }
  }

  fun stopStream() {
    isStreaming = false
    rtpStopStream()
    if (!recordController.isRunning && !isOnPreview) cameraManager.stop()
    microphoneManager.stop()
    videoEncoder.stop()
    audioEncoder.stop()
    glInterface.removeMediaCodecSurface()
    if (!recordController.isRunning && !isOnPreview) glInterface.stop()
  }

  fun startRecord(path: String, listener: RecordController.Listener) {
    recordController.startRecord(path, listener)
    if (!isStreaming) {
      if (!glInterface.running) glInterface.start()
      if (!cameraManager.isRunning()) {
        cameraManager.start(glInterface.getSurfaceTexture())
      }
      microphoneManager.start()
      videoEncoder.start()
      audioEncoder.start()
      glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
    } else {
      videoEncoder.reset()
    }
  }

  fun stopRecord() {
    recordController.stopRecord()
    if (!isStreaming && !isOnPreview) cameraManager.stop()
    microphoneManager.stop()
    videoEncoder.stop()
    audioEncoder.stop()
    glInterface.removeMediaCodecSurface()
    if (!isStreaming && !isOnPreview) glInterface.stop()
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
    if (!cameraManager.isRunning()) {
      cameraManager.start(glInterface.getSurfaceTexture())
    }
    glInterface.attachPreview(surface)
  }

  fun stopPreview() {
    isOnPreview = false
    if (!isStreaming && !recordController.isRunning) cameraManager.stop()
    glInterface.deAttachPreview()
    if (!isStreaming && !recordController.isRunning) glInterface.stop()
  }

  fun changeVideoSource(source: CameraManager.Source) {
    cameraManager.changeSource(source)
  }

  fun setStreamOrientation(orientation: Int) {
    glInterface.setStreamOrientation(orientation)
  }

  fun setPreviewOrientation(orientation: Int) {
    glInterface.setPreviewOrientation(orientation)
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