package com.pedro.rtsp.rtsp.commands

import android.util.Log
import java.util.regex.Pattern

/**
 * Created by pedro on 7/04/21.
 */
data class Command(val method: Method, val cSeq: Int, val status: Int, val text: String) {

  companion object {

    private const val TAG = "Command"

    /**
     * Response received after send a command
     */
    fun parseResponse(method: Method, responseText: String): Command {
      val status = getResponseStatus(responseText)
      val cSeq = getCSeq(responseText)
      return Command(method, cSeq, status, responseText)
    }

    /**
     * Command send by server/player
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
      val matcher = Pattern.compile("(\\w+) (\\S+) RTSP",Pattern.CASE_INSENSITIVE).matcher(response)
      if (matcher.find()) {
        val method = matcher.group(1)
        return if (method != null) {
          when (method.toUpperCase()) {
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
}
