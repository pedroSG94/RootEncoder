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

package com.pedro.rtsp.rtsp.commands

import android.util.Log
import com.pedro.rtsp.rtsp.Protocol
import java.util.regex.Pattern

/**
 * Created by pedro on 13/4/22.
 */
class CommandParser {

  companion object {
    private const val TAG = "CommandParser"
  }

  fun loadServerPorts(command: Command, protocol: Protocol, audioClientPorts: IntArray,
    videoClientPorts: IntArray, audioServerPorts: IntArray, videoServerPorts: IntArray): Boolean {
    var isAudio = true
    if (command.method == Method.SETUP && protocol == Protocol.UDP) {
      val clientPattern = Pattern.compile("client_port=([0-9]+)-([0-9]+)")
      val clientMatcher = clientPattern.matcher(command.text)
      if (clientMatcher.find()) {
        val port = (clientMatcher.group(1) ?: "-1").toInt()
        isAudio = port == audioClientPorts[0]
      }

      val rtspPattern = Pattern.compile("server_port=([0-9]+)-([0-9]+)")
      val matcher = rtspPattern.matcher(command.text)
      if (matcher.find()) {
        if (isAudio) {
          audioServerPorts[0] = matcher.group(1)?.toInt() ?: audioClientPorts[0]
          audioServerPorts[1] = matcher.group(2)?.toInt() ?: audioClientPorts[1]
        } else {
          videoServerPorts[0] = matcher.group(1)?.toInt() ?: videoClientPorts[0]
          videoServerPorts[1] = matcher.group(2)?.toInt() ?: videoClientPorts[1]
        }
        return true
      }
    }
    return false
  }

  fun getSessionId(command: Command): String {
    var sessionId = ""
    val rtspPattern = Pattern.compile("Session:(\\s?[^;\\n]+)")
    val matcher = rtspPattern.matcher(command.text)
    if (matcher.find()) {
      sessionId = matcher.group(1) ?: ""
      val temp = sessionId.split(";")[0]
      sessionId = temp.trim()
    }
    return sessionId
  }
  /**
   * Response received after send a command
   */
  fun parseResponse(method: Method, responseText: String): Command {
    val status = getResponseStatus(responseText)
    val cSeq = getCSeq(responseText)
    return Command(method, cSeq, status, responseText)
  }

  /**
   * Command send by pusher/player
   */
  fun parseCommand(commandText: String): Command {
    val method = getMethod(commandText)
    val cSeq = getCSeq(commandText)
    return Command(method, cSeq, -1, commandText)
  }

  private fun getCSeq(request: String): Int {
    val cSeqMatcher = Pattern.compile("CSeq\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(request)
    return if (cSeqMatcher.find()) {
      cSeqMatcher.group(1)?.toInt() ?: -1
    } else {
      Log.e(TAG, "cSeq not found")
      -1
    }
  }

  private fun getMethod(response: String): Method {
    val matcher = Pattern.compile("(\\w+) (\\S+) RTSP", Pattern.CASE_INSENSITIVE).matcher(response)
    if (matcher.find()) {
      val method = matcher.group(1)
      return if (method != null) {
        when (method.uppercase()) {
          Method.OPTIONS.name -> Method.OPTIONS
          Method.ANNOUNCE.name -> Method.ANNOUNCE
          Method.RECORD.name -> Method.RECORD
          Method.SETUP.name -> Method.SETUP
          Method.DESCRIBE.name -> Method.DESCRIBE
          Method.TEARDOWN.name -> Method.TEARDOWN
          Method.PLAY.name -> Method.PLAY
          Method.PAUSE.name -> Method.PAUSE
          Method.SET_PARAMETERS.name -> Method.SET_PARAMETERS
          Method.GET_PARAMETERS.name -> Method.GET_PARAMETERS
          Method.REDIRECT.name -> Method.REDIRECT
          else -> Method.UNKNOWN
        }
      } else  {
        Method.UNKNOWN
      }
    } else {
      return Method.UNKNOWN
    }
  }

  private fun getResponseStatus(response: String): Int {
    val matcher = Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)", Pattern.CASE_INSENSITIVE).matcher(response)
    return if (matcher.find()) {
      (matcher.group(1) ?: "-1").toInt()
    } else {
      Log.e(TAG, "status code not found")
      -1
    }
  }
}
