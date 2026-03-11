/*
 * Copyright (C) 2024 pedroSG94.
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

abstract class BaseRecordController : RecordController {

  companion object {
    const val TAG: String = "RecordController"
  }

  @JvmField
  @Volatile
  protected var status: RecordController.Status = RecordController.Status.STOPPED
  @JvmField
  protected var videoCodec: VideoCodec = VideoCodec.H264
  @JvmField
  protected var audioCodec: AudioCodec = AudioCodec.AAC
  @JvmField
  protected var pauseMoment: Long = 0
  @JvmField
  protected var pauseTime: Long = 0
  @JvmField
  protected var listener: RecordController.Listener? = null
  @JvmField
  protected var videoTrack: Int = -1
  @JvmField
  protected var audioTrack: Int = -1
  @JvmField
  protected var bitrateManager: BitrateManager? = null
  @JvmField
  @Volatile
  protected var startTs: Long = 0
  @JvmField
  protected var tracks: RecordTracks = RecordTracks.ALL
  protected var myRequestKeyFrame: RequestKeyFrame? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  private var muxerChannel: Channel<MediaFrame>? = null
  private var muxerJob: Job? = null

  fun getStatus() = status

  fun setRequestKeyFrame(requestKeyFrame: RequestKeyFrame?) {
    this.myRequestKeyFrame = requestKeyFrame
  }

  fun updateInfo(recordController: BaseRecordController) {
    videoCodec = recordController.videoCodec
    audioCodec = recordController.audioCodec
  }

  fun setVideoCodec(videoCodec: VideoCodec) {
    this.videoCodec = videoCodec
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    this.audioCodec = audioCodec
  }

  val isRunning: Boolean
    get() = status == RecordController.Status.STARTED || status == RecordController.Status.RECORDING || status == RecordController.Status.RESUMED || status == RecordController.Status.PAUSED

  val isRecording: Boolean
    get() = status == RecordController.Status.RECORDING
  
  fun pauseRecord() {
    if (status == RecordController.Status.RECORDING) {
      pauseMoment = getCurrentTimeMicro()
      status = RecordController.Status.PAUSED
      listener?.onStatusChange(status)
    }
  }

  fun resumeRecord() {
    if (status == RecordController.Status.PAUSED) {
      pauseTime += getCurrentTimeMicro() - pauseMoment
      status = RecordController.Status.RESUMED
      listener?.onStatusChange(status)
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
    val info = videoInfo.toMediaFrameInfo()
    val i = updateFormat(info)
    muxerChannel?.trySend(MediaFrame(videoBuffer.clone(), i, MediaFrame.Type.VIDEO))
  }

  override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
    val info = audioInfo.toMediaFrameInfo()
    val i = updateFormat(info)
    muxerChannel?.trySend(MediaFrame(audioBuffer.clone(), i, MediaFrame.Type.AUDIO))
  }

  override fun startRecord(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    muxerChannel = Channel(Channel.UNLIMITED)
    muxerJob = scope.launch {
      val channel = muxerChannel ?: return@launch
      for (frame in channel) {
        onWriteFrame(frame)
      }
    }
    startRecordImp(fd, listener, tracks)
  }

  override fun startRecord(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    muxerChannel = Channel(Channel.UNLIMITED)
    muxerJob = scope.launch {
      val channel = muxerChannel ?: return@launch
      for (frame in channel) onWriteFrame(frame)
    }
    startRecordImp(path, listener, tracks)
  }

  override fun stopRecord() {
    muxerChannel?.close()
    muxerChannel = null
    muxerJob?.cancel()
    runBlocking { muxerJob?.join() }
    stopRecordImp()
  }

  abstract fun startRecordImp(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordTracks)
  abstract fun startRecordImp(path: String, listener: RecordController.Listener?, tracks: RecordTracks)
  abstract fun stopRecordImp()
  abstract suspend fun onWriteFrame(frame: MediaFrame)
}
