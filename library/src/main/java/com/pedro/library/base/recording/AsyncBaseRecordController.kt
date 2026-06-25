/*
 * Copyright (C) 2026 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pedro.library.base.recording

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateManager
import com.pedro.common.TimeUtils.getCurrentTimeMicro
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaFrameInfo
import com.pedro.library.base.recording.RecordController.RecordTracks
import com.pedro.library.base.recording.RecordController.RequestKeyFrame
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * Record async to avoid block the thread used to send frames to protocol module.
 */
abstract class AsyncBaseRecordController : RecordController {

  companion object {
    const val TAG: String = "AsyncRecordController"
    private const val CAPACITY = 500
  }

  @Volatile
  protected var recordStatus: RecordController.Status = RecordController.Status.STOPPED
  protected var listener: RecordController.Listener? = null
  protected var bitrateManager: BitrateManager? = null
  protected var tracks: RecordTracks = RecordTracks.ALL
  protected var myRequestKeyFrame: RequestKeyFrame? = null
  private var videoCodec: VideoCodec = VideoCodec.H264
  private var audioCodec: AudioCodec = AudioCodec.AAC
  private var pauseMoment: Long = 0
  private var pauseTime: Long = 0
  @Volatile
  private var startTs: Long = 0
  private val scope = CoroutineScope(Dispatchers.IO)
  private var muxerChannel: Channel<MediaFrame>? = null
  private var muxerJob: Job? = null

  override fun setRequestKeyFrame(requestKeyFrame: RequestKeyFrame?) {
    this.myRequestKeyFrame = requestKeyFrame
  }

  override fun updateInfo(videoCodec: VideoCodec, audioCodec: AudioCodec) {
    this.videoCodec = videoCodec
    this.audioCodec = audioCodec
  }

  override fun setVideoCodec(videoCodec: VideoCodec) {
    this.videoCodec = videoCodec
  }

  override fun setAudioCodec(audioCodec: AudioCodec) {
    this.audioCodec = audioCodec
  }

  override fun getVideoCodec(): VideoCodec = videoCodec
  override fun getAudioCodec(): AudioCodec = audioCodec
  override fun isRunning(): Boolean = recordStatus == RecordController.Status.STARTED || recordStatus == RecordController.Status.RECORDING || recordStatus == RecordController.Status.RESUMED || recordStatus == RecordController.Status.PAUSED
  override fun isRecording(): Boolean = recordStatus == RecordController.Status.RECORDING
  override fun getStatus(): RecordController.Status = recordStatus

  override fun pauseRecord() {
    if (recordStatus == RecordController.Status.RECORDING) {
      pauseMoment = getCurrentTimeMicro()
      recordStatus = RecordController.Status.PAUSED
      listener?.onStatusChange(recordStatus)
    }
  }

  override fun resumeRecord() {
    if (recordStatus == RecordController.Status.PAUSED) {
      pauseTime += getCurrentTimeMicro() - pauseMoment
      recordStatus = RecordController.Status.RESUMED
      listener?.onStatusChange(recordStatus)
    }
  }

  protected fun isKeyFrame(videoBuffer: ByteBuffer): Boolean {
    val header = ByteArray(5)
    if (videoBuffer.remaining() < header.size) return false
    videoBuffer.duplicate().get(header, 0, header.size)
    return when (videoCodec) {
      VideoCodec.AV1 -> {
        //TODO find the way to check it
        false
      }
      VideoCodec.H264 if (header[4].toInt() and 0x1F) == RtpConstants.IDR -> {  //h264
        true
      }
      else -> { //h265
        (videoCodec == VideoCodec.H265
            && ((header[4].toInt() shr 1) and 0x3f) == RtpConstants.IDR_W_DLP
            || ((header[4].toInt() shr 1) and 0x3f) == RtpConstants.IDR_N_LP)
      }
    }
  }

  private fun updateFormat(oldInfo: MediaFrame.Info): MediaFrame.Info {
    if (startTs <= 0) startTs = oldInfo.timestamp
    val ts = max(0, oldInfo.timestamp - startTs - pauseTime)
    return oldInfo.copy(timestamp = ts)
  }

  override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
    sendFrame(videoBuffer, videoInfo, MediaFrame.Type.VIDEO)
  }

  override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
    sendFrame(audioBuffer, audioInfo, MediaFrame.Type.AUDIO)
  }

  private fun sendFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: MediaFrame.Type) {
    if (recordStatus == RecordController.Status.STOPPED) return
    val frameInfo = info.toMediaFrameInfo()
    val i = updateFormat(frameInfo)
    muxerChannel?.trySend(MediaFrame(buffer.clone(), i, type))
  }

  override fun startRecord(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    start(listener, tracks)
    try {
      startRecordImp(fd, listener, tracks)
    } catch (e: Exception) {
      stopRecord()
      throw e
    }
  }

  override fun startRecord(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    start(listener, tracks)
    try {
      startRecordImp(path, listener, tracks)
    } catch (e: Exception) {
      stopRecord()
      throw e
    }
  }

  private fun start(
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    clearTimestamp()
    muxerChannel = Channel(CAPACITY)
    muxerJob = scope.launch {
      val channel = muxerChannel ?: return@launch
      for (frame in channel) onWriteFrame(frame)
    }
    this.tracks = tracks
    this.listener = listener
    recordStatus = RecordController.Status.STARTED
    if (listener != null) {
      bitrateManager = BitrateManager(listener)
      listener.onStatusChange(recordStatus)
    } else {
      bitrateManager = null
    }
  }

  override fun stopRecord() {
    muxerChannel?.close()
    muxerChannel = null
    muxerJob?.cancel()
    runBlocking { muxerJob?.join() }
    recordStatus = RecordController.Status.STOPPED
    clearTimestamp()
    myRequestKeyFrame = null
    listener?.onStatusChange(recordStatus)
    stopRecordImp()
  }

  private fun clearTimestamp() {
    pauseMoment = 0
    pauseTime = 0
    startTs = 0
  }

  abstract fun startRecordImp(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordTracks)
  abstract fun startRecordImp(path: String, listener: RecordController.Listener?, tracks: RecordTracks)
  abstract fun stopRecordImp()
  abstract suspend fun onWriteFrame(frame: MediaFrame)
}
