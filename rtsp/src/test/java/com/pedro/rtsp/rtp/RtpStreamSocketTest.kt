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

package com.pedro.rtsp.rtp

import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Created by pedro on 9/9/23.
 */
@RunWith(MockitoJUnitRunner::class)
class RtpStreamSocketTest {

  @Mock
  private lateinit var udpSocket: UdpStreamSocket
  @Mock
  private lateinit var tcpSocket: TcpStreamSocket

  @Test
  fun `GIVEN multiple video or audio rtp frames WHEN update rtcp tcp send THEN send only 1 of video and 1 of audio each 3 seconds`() = runTest {
    val senderReportTcp = BaseRtpSocket.getInstance(SocketType.JAVA, Protocol.TCP, "127.0.0.1", 0, 1, 2, 3)
    senderReportTcp.setSocket(tcpSocket)
    val fakeFrameVideo = RtpFrame(byteArrayOf(0x00, 0x00, 0x00), 0, 3, RtpConstants.trackVideo)
    val fakeFrameAudio = RtpFrame(byteArrayOf(0x00, 0x00, 0x00), 0, 3, RtpConstants.trackAudio)
    (0 until 10).forEach { value ->
      val frame = if (value % 2 == 0) fakeFrameVideo else fakeFrameAudio
      senderReportTcp.sendFrame(frame)
    }
    val resultValue = argumentCaptor<ByteArray>()
    withContext(Dispatchers.IO) {
      verify(tcpSocket, times((10))).write(resultValue.capture())
    }
  }

  @Test
  fun `GIVEN multiple video or audio rtp frames WHEN update rtcp udp send THEN send only 1 of video and 1 of audio each 3 seconds`() = runTest {
    val senderReportUdp = RtpSocketUdp(udpSocket, udpSocket)
    senderReportUdp.setSocket(tcpSocket)
    val fakeFrameVideo = RtpFrame(byteArrayOf(0x00, 0x00, 0x00), 0, 3, RtpConstants.trackVideo)
    val fakeFrameAudio = RtpFrame(byteArrayOf(0x00, 0x00, 0x00), 0, 3, RtpConstants.trackAudio)
    (0 until 10).forEach { value ->
      val frame = if (value % 2 == 0) fakeFrameVideo else fakeFrameAudio
      senderReportUdp.sendFrame(frame)
    }
    val resultValue = argumentCaptor<ByteArray>()
    withContext(Dispatchers.IO) {
      verify(udpSocket, times((10))).write(resultValue.capture())
    }
  }
}