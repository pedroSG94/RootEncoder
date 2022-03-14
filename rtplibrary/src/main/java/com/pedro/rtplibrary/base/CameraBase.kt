package com.pedro.rtplibrary.base

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.SurfaceView
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

  enum class CameraSource{
    CAMERA1, CAMERA2
  }

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
  private var streaming = false

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

  }

  fun stopStream() {

  }

  fun startRecord(path: String) {

  }

  fun stopRecord() {

  }

  fun startPreview(surfaceView: SurfaceView) {
    if (!glInterface.running) glInterface.start()
    if (!cameraManager.isRunning()) {
      cameraManager.start(glInterface.getSurfaceTexture())
    }
    glInterface.attachPreview(surfaceView)
  }

  fun stopPreview() {
    if (!streaming) cameraManager.stop()
    glInterface.deAttachPreview()
    if (!streaming) glInterface.stop()
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

  protected abstract fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
  protected abstract fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
  protected abstract fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
}