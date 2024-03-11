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

package com.pedro.rtsp.rtsp

import com.pedro.rtsp.rtsp.commands.Command
import com.pedro.rtsp.rtsp.commands.CommandParser
import com.pedro.rtsp.rtsp.commands.Method
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 14/4/22.
 */
class CommandParserTest {

  private val commandParser = CommandParser()

  @Test
  fun `GIVEN a SETUP command with text from server WHEN parse using udp protocol THEN change server ports`() {
    val audioClientPorts = intArrayOf(5000, 5001)
    val videoClientPorts = intArrayOf(5002, 5003)
    val audioServerPorts = intArrayOf(5004, 5005)
    val videoServerPorts = intArrayOf(5006, 5007)

    val expectedAudioServerPorts = intArrayOf(5014, 5015)
    val expectedVideoServerPorts = intArrayOf(5016, 5017)
    val serverCommandAudio = "RTSP/1.0 200 OK\r\n" +
        "Date: Fri, 15 Apr 2022 16:16:30 UTC\r\n" +
        "Expires: Fri, 15 Apr 2022 16:16:30 UTC\r\n" +
        "Transport: RTP/AVP/UDP;unicast;client_port=${audioClientPorts[0]}-${audioClientPorts[1]};server_port=${expectedAudioServerPorts[0]}-${expectedAudioServerPorts[1]}\r\n" +
        "Session: 57168112;timeout=60\r\n" +
        "CSeq: 3\r\n" +
        "Cache-Control: no-cache\r\n\r\n"
    val serverCommandVideo = "RTSP/1.0 200 OK\r\n" +
        "Date: Fri, 15 Apr 2022 16:16:30 UTC\r\n" +
        "Expires: Fri, 15 Apr 2022 16:16:30 UTC\r\n" +
        "Transport: RTP/AVP/UDP;unicast;client_port=${videoClientPorts[0]}-${videoClientPorts[1]};server_port=${expectedVideoServerPorts[0]}-${expectedVideoServerPorts[1]}\r\n" +
        "Session: 57168112;timeout=60\r\n" +
        "CSeq: 3\r\n" +
        "Cache-Control: no-cache\r\n\r\n"
    val commandAudio = Command(Method.SETUP, 1, 200, serverCommandAudio)
    val commandVideo = Command(Method.SETUP, 1, 200, serverCommandVideo)

    commandParser.loadServerPorts(commandAudio, Protocol.UDP, audioClientPorts, videoClientPorts, audioServerPorts, videoServerPorts)
    commandParser.loadServerPorts(commandVideo, Protocol.UDP, audioClientPorts, videoClientPorts, audioServerPorts, videoServerPorts)
    assertArrayEquals(audioServerPorts, expectedAudioServerPorts)
    assertArrayEquals(videoServerPorts, expectedVideoServerPorts)
  }

  @Test
  fun `GIVEN a command with sessionId from server WHEN parse THEN get a sessionId`() {
    val expectedSessionId = "7d73E09a-096 a?$<>_!.,^+*%&05/()=42"
    val serverCommand = "RECORD rtsp://192.168.0.196:80/live/pedro RTSP/1.0\r\n" +
        "Range: npt=0.000-\r\n" +
        "CSeq: 5\r\n" +
        "User-Agent: com.pedro.rtsp 2.1.7\r\n" +
        "Session: $expectedSessionId;timeout=30\r\n\r\n"
    val command = Command(Method.UNKNOWN, 1, 200, serverCommand)
    val result = commandParser.getSessionId(command)
    assertEquals(expectedSessionId, result)
  }

  @Test
  fun `GIVEN a command with sessionId from server WHEN parse THEN get a sessionId2`() {
    val expectedSessionId = "7d73E09a-096 a?$<>_!.,^+*%&05/()=42"
    val serverCommand = "RECORD rtsp://192.168.0.196:80/live/pedro RTSP/1.0\r\n" +
            "Range: npt=0.000-\r\n" +
            "CSeq: 5\r\n" +
            "Session: $expectedSessionId\r\n" +
            "User-Agent: com.pedro.rtsp 2.1.7\r\n\r\n"
    val command = Command(Method.UNKNOWN, 1, 200, serverCommand)
    val result = commandParser.getSessionId(command)
    assertEquals(expectedSessionId, result)
  }

  @Test
  fun `GIVEN server command string WHEN parse THEN get a command with cSeq and status`() {
    val serverCommand = "RTSP/1.0 200 OK\r\n" +
        "Server: pedroSG94 Server\r\n" +
        "CSeq: 1\r\n" +
        "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, ANNOUNCE, RECORD\r\n" +
        "\r\n"
    val command = commandParser.parseResponse(Method.OPTIONS, serverCommand)
    val expectedCommand = Command(Method.OPTIONS, 1, 200, serverCommand)
    assertEquals(expectedCommand, command)
  }

  @Test
  fun `GIVEN pusher or player command string WHEN parse THEN get a command with cSeq and method`() {
    val pusherCommand = "OPTIONS rtsp://192.168.1.132:554/live/pedro RTSP/1.0\r\n" +
        "CSeq: 1\r\n" +
        "\r\n"
    val command = commandParser.parseCommand(pusherCommand)
    val expectedCommand = Command(Method.OPTIONS, 1, -1, pusherCommand)
    assertEquals(expectedCommand, command)
  }
}