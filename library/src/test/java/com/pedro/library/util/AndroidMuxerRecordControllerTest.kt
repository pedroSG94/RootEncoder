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

package com.pedro.library.util

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import com.pedro.library.base.recording.RecordController.Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * When each muxer starts depending on the tracks recorded. The muxer only starts in the muxer
 * thread, so the frames are the ones that move the status from STARTED to RECORDING.
 *
 * MediaMuxer is the android.jar stub here (it does nothing), so the status is what we check.
 */
@RunWith(Parameterized::class)
class AndroidMuxerRecordControllerTest(
  @Suppress("unused") private val name: String,
  private val createController: () -> RecordController
) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun controllers(): List<Array<Any>> {
      val mp4: () -> RecordController = { AndroidMuxerRecordController() }
      val webm: () -> RecordController = {
        AndroidMuxerWebmRecordController().apply {
          setVideoCodec(VideoCodec.VP8)
          setAudioCodec(AudioCodec.OPUS)
        }
      }
      return listOf(arrayOf("mp4", mp4), arrayOf("webm", webm))
    }
  }

  private val keyFrame = byteArrayOf(0, 0, 0, 1, 0x65, 0x08, 0x04, 0x00)
  private val nonKeyFrame = byteArrayOf(0, 0, 0, 1, 0x41, 0x1A, 0x02, 0x00)
  private val audioFrame = byteArrayOf(0x21, 0x10, 0x05, 0x40)

  private val statuses = LinkedBlockingQueue<Status>()
  private val listener = RecordController.Listener { statuses.add(it) }
  private val controller = createController()

  @After
  fun tearDown() {
    controller.stopRecord()
  }

  private fun awaitStatus(expected: Status) {
    while (true) {
      val status = statuses.poll(1000, TimeUnit.MILLISECONDS)
      assertNotNull("timeout waiting for $expected", status)
      if (status == expected) return
    }
  }

  private fun sendVideo(frame: ByteArray, isKeyFrame: Boolean) {
    controller.recordVideo(ByteBuffer.wrap(frame), MediaCodec.BufferInfo().apply {
      offset = 0
      size = frame.size
      presentationTimeUs = 0
      flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
    })
  }

  private fun sendAudio() {
    controller.recordAudio(ByteBuffer.wrap(audioFrame), MediaCodec.BufferInfo().apply {
      offset = 0
      size = audioFrame.size
      presentationTimeUs = 0
      flags = 0
    })
  }

  @Test
  fun `GIVEN audio only and the audio format after start WHEN an audio frame arrives THEN start recording`() {
    controller.startRecord("fake", listener, RecordTracks.AUDIO)
    //as when the record starts before the audio encoder, the format comes later
    controller.setAudioFormat(MediaFormat())
    sendAudio()

    awaitStatus(Status.RECORDING)
  }

  @Test
  fun `GIVEN audio only with video frames WHEN video arrives THEN video never starts the record`() {
    val requestedKeyFrame = AtomicBoolean(false)
    controller.setRequestKeyFrame { requestedKeyFrame.set(true) }
    controller.setVideoFormat(MediaFormat())
    controller.setAudioFormat(MediaFormat())
    controller.startRecord("fake", listener, RecordTracks.AUDIO)
    sendVideo(nonKeyFrame, false)
    sendAudio()

    awaitStatus(Status.RECORDING)
    //the video frame went through the muxer thread before the audio one and must be ignored
    assertFalse(requestedKeyFrame.get())
  }

  @Test
  fun `GIVEN audio only WHEN pause and resume THEN the next audio frame records again`() {
    controller.setAudioFormat(MediaFormat())
    controller.startRecord("fake", listener, RecordTracks.AUDIO)
    sendAudio()
    awaitStatus(Status.RECORDING)

    controller.pauseRecord()
    awaitStatus(Status.PAUSED)
    controller.resumeRecord()
    awaitStatus(Status.RESUMED)
    sendAudio()

    awaitStatus(Status.RECORDING)
    assertEquals(Status.RECORDING, controller.getStatus())
  }

  @Test
  fun `GIVEN video and audio WHEN audio arrives first THEN wait for a video keyframe`() {
    val statusOnRequest = AtomicReference<Status>()
    controller.setRequestKeyFrame { statusOnRequest.set(controller.getStatus()) }
    controller.setVideoFormat(MediaFormat())
    controller.setAudioFormat(MediaFormat())
    controller.startRecord("fake", listener, RecordTracks.ALL)
    sendAudio()
    //handled after the audio frame, asks for a keyframe
    sendVideo(nonKeyFrame, false)
    sendVideo(keyFrame, true)

    awaitStatus(Status.RECORDING)
    //the audio frame alone must not start the record
    assertEquals(Status.STARTED, statusOnRequest.get())
  }

  @Test
  fun `GIVEN video only WHEN a keyframe arrives THEN start recording without audio format`() {
    controller.setVideoFormat(MediaFormat())
    controller.startRecord("fake", listener, RecordTracks.VIDEO)
    sendVideo(keyFrame, true)

    awaitStatus(Status.RECORDING)
  }
}
