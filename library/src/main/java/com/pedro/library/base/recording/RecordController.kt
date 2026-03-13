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
import android.media.MediaFormat
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateChecker
import com.pedro.common.VideoCodec
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

interface RecordController {
  @Throws(IOException::class)
  fun startRecord(path: String, listener: Listener?, tracks: RecordTracks)
  @Throws(IOException::class)
  fun startRecord(fd: FileDescriptor, listener: Listener?, tracks: RecordTracks)
  fun stopRecord()
  fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo)
  fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo)
  fun setVideoFormat(videoFormat: MediaFormat)
  fun setAudioFormat(audioFormat: MediaFormat)
  fun resetFormats()
  fun isRunning(): Boolean
  fun isRecording(): Boolean
  fun setVideoCodec(videoCodec: VideoCodec)
  fun setAudioCodec(audioCodec: AudioCodec)
  fun pauseRecord()
  fun resumeRecord()
  fun updateInfo(videoCodec: VideoCodec, audioCodec: AudioCodec)
  fun getVideoCodec(): VideoCodec
  fun getAudioCodec(): AudioCodec
  fun setRequestKeyFrame(requestKeyFrame: RequestKeyFrame?)
  fun getStatus(): Status

  fun interface Listener : BitrateChecker {
    fun onStatusChange(status: Status)
    fun onError(e: Exception?) {
      Log.i(AsyncBaseRecordController.TAG, "Write error", e)
    }
  }

  fun interface RequestKeyFrame {
    fun onRequestKeyFrame()
  }

  enum class Status {
    STARTED, STOPPED, RECORDING, PAUSED, RESUMED
  }

  enum class RecordTracks {
    ALL, VIDEO, AUDIO
  }
}
