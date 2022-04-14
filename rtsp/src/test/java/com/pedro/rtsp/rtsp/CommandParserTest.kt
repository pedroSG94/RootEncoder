package com.pedro.rtsp.rtsp

import com.pedro.rtsp.BuildConfig
import com.pedro.rtsp.rtsp.commands.Command
import com.pedro.rtsp.rtsp.commands.CommandParser
import com.pedro.rtsp.rtsp.commands.Method
import junit.framework.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 14/4/22.
 */
class CommandParserTest {

  @Test
  fun `GIVEN server command string WHEN parse THEN get a command with cSeq and status`() {
    val serverCommand = "RTSP/1.0 200 OK\r\n" +
        "Server: pedroSG94 Server\r\n" +
        "CSeq: 1\r\n" +
        "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, ANNOUNCE, RECORD\r\n" +
        "\r\n"
    val commandParser = CommandParser()
    val command = commandParser.parseResponse(Method.OPTIONS, serverCommand)
    val expectedCommand = Command(Method.OPTIONS, 1, 200, serverCommand)
    assertEquals(expectedCommand, command)
  }

  @Test
  fun `GIVEN pusher or player command string WHEN parse THEN get a command with cSeq and method`() {
    val pusherCommand = "OPTIONS rtsp://192.168.1.132:554/live/pedro RTSP/1.0\r\n" +
        "CSeq: 1\r\n" +
        "User-Agent: ${BuildConfig.LIBRARY_PACKAGE_NAME} ${BuildConfig.VERSION_NAME}\r\n" +
        "\r\n"
    val commandParser = CommandParser()
    val command = commandParser.parseCommand(pusherCommand)
    val expectedCommand = Command(Method.OPTIONS, 1, -1, pusherCommand)
    assertEquals(expectedCommand, command)
  }
}