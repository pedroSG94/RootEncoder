/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.rtplibrary.srt

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.rtplibrary.base.StreamBase
import com.pedro.rtplibrary.util.sources.AudioManager
import com.pedro.rtplibrary.util.sources.VideoManager
import com.pedro.srt.srt.SrtClient
import com.pedro.srt.srt.VideoCodec
import com.pedro.srt.utils.ConnectCheckerSrt
import java.nio.ByteBuffer

/**
 * Created by pedro on 8/9/23.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SrtStream(context: Context, connectCheckerRtmp: ConnectCheckerSrt, videoSource: VideoManager.Source,
                audioSource: AudioManager.Source): StreamBase(context, videoSource, audioSource) {

  constructor(context: Context, connectCheckerRtmp: ConnectCheckerSrt):
      this(context, connectCheckerRtmp, VideoManager.Source.CAMERA2, AudioManager.Source.MICROPHONE)

  private val srtClient = SrtClient(connectCheckerRtmp)

  fun setVideoCodec(videoCodec: VideoCodec) {
    val mime = if (videoCodec === VideoCodec.H265) CodecUtil.H265_MIME else CodecUtil.H264_MIME
    super.setVideoMime(mime)
    srtClient.setVideoCodec(videoCodec)
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    srtClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) {
    srtClient.connect(endPoint)
  }

  override fun rtpStopStream() {
    srtClient.disconnect()
  }

  override fun setAuthorization(user: String?, password: String?) {
    srtClient.setAuthorization(user, password)
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    srtClient.setVideoInfo(sps, pps, vps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    srtClient.sendVideo(h264Buffer, info)
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    srtClient.sendAudio(aacBuffer, info)
  }

  override fun setReTries(reTries: Int) {
    srtClient.setReTries(reTries)
  }

  override fun shouldRetry(reason: String): Boolean = srtClient.shouldRetry(reason)

  override fun reConnect(delay: Long, backupUrl: String?) {
    srtClient.reConnect(delay, backupUrl)
  }

  override fun hasCongestion(): Boolean = srtClient.hasCongestion()

  override fun setLogs(enabled: Boolean) {
    srtClient.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
    srtClient.setCheckServerAlive(enabled)
  }

  override fun resizeCache(newSize: Int) {
    srtClient.resizeCache(newSize)
  }

  override fun getCacheSize(): Int = srtClient.cacheSize

  override fun getSentAudioFrames(): Long = srtClient.sentAudioFrames

  override fun getSentVideoFrames(): Long = srtClient.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = srtClient.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = srtClient.droppedVideoFrames

  override fun resetSentAudioFrames() {
    srtClient.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    srtClient.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    srtClient.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    srtClient.resetDroppedVideoFrames()
  }
}