/*
 * Copyright (C) 2021 pedroSG94.
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Created by pedro on 15/4/22.
 */
class SdpBodyTest {

  @Test
  fun `GIVEN aac info WHEN create aac body THEN get expected string`() {
    val track = 1
    val sampleRate = 44100
    val channels = 2

    val expectedType = "MPEG4-GENERIC/$sampleRate/$channels"
    val expectedConfig = "config=1210;"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createAacBody(track, sampleRate, channels == 2)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN h264 info WHEN create h264 body THEN get expected string`() {
    val track = 1
    val sps = "abcd1234"
    val pps = "efgh5678"

    val expectedType = "H264/${RtpConstants.clockVideoFrequency}"
    val expectedConfig = "sprop-parameter-sets=$sps,$pps"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createH264Body(track, sps, pps)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }

  @Test
  fun `GIVEN h265 info WHEN create h265 body THEN get expected string`() {
    val track = 1
    val sps = "abcd1234"
    val pps = "efgh5678"
    val vps = "ijk90"

    val expectedType = "H265/${RtpConstants.clockVideoFrequency}"
    val expectedConfig = "sprop-sps=$sps; sprop-pps=$pps; sprop-vps=$vps"
    val expectedPayload = "a=rtpmap:${RtpConstants.payloadType + track}"
    val expectedTrack = "a=control:streamid=${track}"

    val result = SdpBody.createH265Body(track, sps, pps, vps)
    assertTrue(result.contains(expectedType))
    assertTrue(result.contains(expectedConfig))
    assertTrue(result.contains(expectedPayload))
    assertTrue(result.contains(expectedTrack))
  }
}