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

package com.pedro.rtsp.rtsp

import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 15/4/22.
 */
class SdpBodyTest {

  //the parameter sets arrive from the csd of the MediaCodec so they contain the start code
  private fun nal(vararg bytes: Int) = ByteBuffer.wrap(
    ByteArray(bytes.size + 4) { if (it < 4) (if (it == 3) 1 else 0).toByte() else bytes[it - 4].toByte() }
  )

  //high profile level 3.0 sps
  private fun spsH264() = nal(
    0x67, 0x64, 0x00, 0x1E, 0xAC, 0xB4, 0x0F, 0x02, 0x8D,
    0x35, 0x02, 0x02, 0x02, 0x07, 0x8B, 0x17, 0x08
  )
  private fun ppsH264() = nal(0x68, 0xEE, 0x0D, 0x8B)

  private fun spsH265() = nal(0x42, 0x01, 0x01, 0x01)
  private fun ppsH265() = nal(0x44, 0x01, 0xC0, 0x66)
  private fun vpsH265() = nal(0x40, 0x01, 0x0C, 0x01)

  //sequence header obu of an av1 stream
  private fun headerAV1() = ByteBuffer.wrap(
    byteArrayOf(
      0x0a, 0x0d, 0x00, 0x00, 0x00, 0x24, 0x4f, 0x7e,
      0x7f, 0x00, 0x68, 0x83.toByte(), 0x00, 0x83.toByte(), 0x02
    )
  )

  @Test
  fun `GIVEN opus info WHEN create opus body THEN get expected string`() {
    val track = 1

    val expectedType = "OPUS/48000/2"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createOpusBody(track, 48000, true)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN opus info WHEN create opus body THEN get expected sprop parameters`() {
    val track = 1
    val payload = RtpConstants.payloadType + track

    //rtpmap always announce 48khz stereo, the real values only go in the fmtp
    val expectedType = "OPUS/48000/2"

    val stereo48k = SdpBody.createOpusBody(track, 48000, true)
    assertTrue(stereo48k.contains(expectedType))
    assertTrue(stereo48k.contains("a=fmtp:$payload sprop-stereo=1; sprop-maxcapturerate=48000\r\n"))

    val mono16k = SdpBody.createOpusBody(track, 16000, false)
    assertTrue(mono16k.contains(expectedType))
    assertTrue(mono16k.contains("a=fmtp:$payload sprop-stereo=0; sprop-maxcapturerate=16000\r\n"))
  }

  @Test
  fun `GIVEN aac info WHEN create aac body THEN get expected string`() {
    val track = 1
    val sampleRate = 44100
    val channels = 2

    val expectedType = "MPEG4-GENERIC/$sampleRate/$channels"
    val expectedConfig = "config=1210;"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createAacBody(track, sampleRate, channels == 2, false)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN g711 info WHEN create g711 body THEN get expected string`() {
    val track = 1

    //the payload type 8 is statically mapped to 8khz mono so it never depends on the encoder config
    val expectedType = "PCMA/8000/1"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadTypeG711}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createG711Body(track)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN h264 info WHEN create h264 body THEN get expected string`() {
    val track = 1

    val expectedType = "H264/${RtpConstants.clockVideoFrequency}"
    //base64 of the nal units without the start code
    val expectedConfig = "sprop-parameter-sets=Z2QAHqy0DwKNNQICAgeLFwg=,aO4Niw=="
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createH264Body(track, spsH264(), ppsH264())
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN h264 sps WHEN create h264 body THEN get expected profile level id`() {
    val track = 1

    //profile_idc 0x64, profile-iop 0x00 and level_idc 0x1E of the sps
    val result = SdpBody.createH264Body(track, spsH264(), ppsH264())
    assertTrue(result.contains("profile-level-id=64001e"))
  }

  @Test
  fun `GIVEN the same buffers WHEN create h264 body twice THEN get the same result`() {
    val track = 1
    //createBody runs again on every ANNOUNCE and on every retry reusing the stored buffers
    val sps = spsH264()
    val pps = ppsH264()

    val first = SdpBody.createH264Body(track, sps, pps)
    val second = SdpBody.createH264Body(track, sps, pps)
    assertEquals(first, second)
  }

  @Test
  fun `GIVEN secured body WHEN create body THEN get the ice discard port`() {
    val track = 1

    //with ICE the port is a placeholder and 0 would mean that the media is rejected
    assertTrue(SdpBody.createOpusBody(track, 48000, true, true).contains("m=audio 9 "))
    assertTrue(SdpBody.createG711Body(track, true).contains("m=audio 9 "))
    assertTrue(SdpBody.createH264Body(track, spsH264(), ppsH264(), true).contains("m=video 9 "))
    assertTrue(SdpBody.createVp8Body(track, true).contains("m=video 9 "))
    assertTrue(SdpBody.createAV1Body(track, headerAV1(), true).contains("m=video 9 "))

    //in RTSP the transport is negotiated in the SETUP so the port stays as 0
    assertTrue(SdpBody.createOpusBody(track, 48000, true).contains("m=audio 0 "))
    assertTrue(SdpBody.createH264Body(track, spsH264(), ppsH264()).contains("m=video 0 "))
  }

  @Test
  fun `GIVEN h265 info WHEN create h265 body THEN get expected string`() {
    val track = 1

    val expectedType = "H265/${RtpConstants.clockVideoFrequency}"
    val expectedConfig = "sprop-sps=QgEBAQ==; sprop-pps=RAHAZg==; sprop-vps=QAEMAQ=="
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createH265Body(track, spsH265(), ppsH265(), vpsH265())
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
    //the base64 of a leading start code would start with AAAA
    assertFalse(result.contains("=AAAA"))
  }

  @Test
  fun `GIVEN AV1 info WHEN create AV1 body THEN get expected string`() {
    val track = 1

    val expectedType = "AV1/${RtpConstants.clockVideoFrequency}"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createAV1Body(track, headerAV1())
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
    //seq_profile and seq_level_idx of the obu payload. Parsing the raw obu instead would report 8
    assertTrue(result.contains("a=fmtp:${RtpConstants.payloadType + track} profile=0; level-idx=4\r\n"))
  }

  @Test
  fun `GIVEN the same buffer WHEN create AV1 body twice THEN get the same result`() {
    val track = 1
    //createBody runs again on every ANNOUNCE and on every retry reusing the stored buffer
    val header = headerAV1()

    val first = SdpBody.createAV1Body(track, header)
    val second = SdpBody.createAV1Body(track, header)
    assertEquals(first, second)
  }
}
