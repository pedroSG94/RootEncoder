package com.pedro.rtplibrary.base

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.util.CameraManager
import com.pedro.rtplibrary.view.OffScreenGlThread
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
class CameraBase(context: Context): GetVideoData, GetAacData, GetMicrophoneData {

  enum class CameraSource{
    CAMERA1, CAMERA2
  }

  //video and audio encoders
  private val videoEncoder = VideoEncoder(this)
  private val audioEncoder = AudioEncoder(this)
  //video render
  private var glInterface = OffScreenGlThread(context)
  //video and audio sources
  private val cameraManager: CameraManager
  private val microphoneManager = MicrophoneManager(this)

  init {
    glInterface.init()
    cameraManager = CameraManager(context, glInterface.surfaceTexture)
  }

  fun prepareVideo(width: Int, height: Int, bitrate: Int, fps: Int = 30, iFrameInterval: Int = 2): Boolean {
    return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, 0,
      iFrameInterval, FormatVideoEncoder.SURFACE)
  }

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

  fun startPreview() {

  }

  fun stopPreview() {

  }

  override fun getAacData(aacBuffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {

  }

  override fun onAudioFormat(mediaFormat: MediaFormat?) {

  }

  override fun inputPCMData(frame: Frame?) {

  }

  override fun onSpsPpsVps(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {

  }

  override fun getVideoData(h264Buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {

  }

  override fun onVideoFormat(mediaFormat: MediaFormat?) {

  }
}