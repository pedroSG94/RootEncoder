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

package com.pedro.rtsp.rtsp.commands

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.getMd5Hash
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.commands.SdpBody.createAV1Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createAacBody
import com.pedro.rtsp.rtsp.commands.SdpBody.createG711Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH264Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH265Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createOpusBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import java.io.BufferedReader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 * Created by pedro on 12/02/19.
 *
 * Class to create request to server and parse response from server.
 */
open class CommandsManager {

  var host: String? = null
    private set
  var port = 0
    private set
  var path: String? = null
    private set
  var sps: ByteArray? = null
    private set
  var pps: ByteArray? = null
    private set
  private var cSeq = 0
  private var sessionId: String? = null
  private val timeStamp: Long
  var sampleRate = 32000
  var isStereo = true
  var protocol: Protocol = Protocol.TCP
  var videoDisabled = false
  var audioDisabled = false
  private val commandParser = CommandParser()
  var videoCodec = VideoCodec.H264
  var audioCodec = AudioCodec.AAC
  //For udp
  val audioClientPorts = intArrayOf(5000, 5001)
  val videoClientPorts = intArrayOf(5002, 5003)
  val audioServerPorts = intArrayOf(5004, 5005)
  val videoServerPorts = intArrayOf(5006, 5007)

  //For H265
  var vps: ByteArray? = null
    private set

  //For auth
  var user: String? = null
    private set
  var password: String? = null
    private set

  companion object {
    private const val TAG = "CommandsManager"
    private var authorization: String? = null
  }

  init {
    val uptime = TimeUtils.getCurrentTimeMillis()
    timeStamp = uptime / 1000 shl 32 and ((uptime - uptime / 1000 * 1000 shr 32)
        / 1000) // NTP timestamp
  }

  fun videoInfoReady(): Boolean {
    return when (videoCodec) {
      VideoCodec.H264 -> sps != null && pps != null
      VideoCodec.H265 -> sps != null && pps != null && vps != null
      VideoCodec.AV1 -> sps != null
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    this.sps = sps.getData()
    this.pps = pps?.getData()
    this.vps = vps?.getData()
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    this.isStereo = isStereo
    this.sampleRate = sampleRate
  }

  fun setAuth(user: String?, password: String?) {
    this.user = user
    this.password = password
  }

  fun setUrl(host: String?, port: Int, path: String?) {
    this.host = host
    this.port = port
    this.path = path
  }

  fun clear() {
    sps = null
    pps = null
    vps = null
    retryClear()
  }

  fun retryClear() {
    cSeq = 0
    sessionId = null
  }

  private val spsString: String
    get() = sps?.encodeToString() ?: ""
  private val ppsString: String
    get() = pps?.encodeToString() ?: ""
  private val vpsString: String
    get() = vps?.encodeToString() ?: ""

  private fun addHeaders(): String {
    return "CSeq: ${++cSeq}\r\n" +
        (if (sessionId.isNullOrEmpty()) "" else "Session: $sessionId\r\n") +
        (if (authorization.isNullOrEmpty()) "" else "Authorization: $authorization\r\n")
  }

  private fun createBody(): String {
    var videoBody = ""
    if (!videoDisabled) {
      videoBody = when (videoCodec) {
        VideoCodec.H264 -> {
          createH264Body(RtpConstants.trackVideo, spsString, ppsString)
        }
        VideoCodec.H265 -> {
          createH265Body(RtpConstants.trackVideo, spsString, ppsString, vpsString)
        }
        VideoCodec.AV1 -> {
          createAV1Body(RtpConstants.trackVideo)
        }
      }
    }
    var audioBody = ""
    if (!audioDisabled) {
      audioBody = when (audioCodec) {
        AudioCodec.G711 -> createG711Body(RtpConstants.trackAudio, sampleRate, isStereo)
        AudioCodec.AAC -> createAacBody(RtpConstants.trackAudio, sampleRate, isStereo)
        AudioCodec.OPUS -> createOpusBody(RtpConstants.trackAudio)
      }
    }
    return "v=0\r\n" +
        "o=- $timeStamp $timeStamp IN IP4 127.0.0.1\r\n" +
        "s=Unnamed\r\n" +
        "i=N/A\r\n" +
        "c=IN IP4 $host\r\n" +
        "t=0 0\r\n" +
        "a=recvonly\r\n" +
        videoBody + audioBody
  }

  private fun createAuth(authResponse: String): String {
    val authPattern = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"", Pattern.CASE_INSENSITIVE)
    val matcher = authPattern.matcher(authResponse)
    //digest auth
    return if (matcher.find()) {
      Log.i(TAG, "using digest auth")
      val realm = matcher.group(1)
      val nonce = matcher.group(2)
      val hash1 = "$user:$realm:$password".getMd5Hash()
      val hash2 = "ANNOUNCE:rtsp://$host:$port$path".getMd5Hash()
      val hash3 = "$hash1:$nonce:$hash2".getMd5Hash()
      "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"rtsp://$host:$port$path\", response=\"$hash3\""
      //basic auth
    } else {
      Log.i(TAG, "using basic auth")
      val data = "$user:$password"
      val base64Data = data.toByteArray().encodeToString()
      "Basic $base64Data"
    }
  }

  //Commands
  fun createOptions(): String {
    val options = "OPTIONS rtsp://$host:$port$path RTSP/1.0\r\n" + addHeaders() + "\r\n"
    Log.i(TAG, options)
    return options
  }

  open fun createSetup(track: Int): String {
    val udpPorts = if (track == RtpConstants.trackVideo) videoClientPorts else audioClientPorts
    val params = if (protocol === Protocol.UDP) {
      "UDP;unicast;client_port=${udpPorts[0]}-${udpPorts[1]};mode=record"
    } else {
      "TCP;unicast;interleaved=${2 * track}-${2 * track + 1};mode=record"
    }
    val setup = "SETUP rtsp://$host:$port$path/streamid=$track RTSP/1.0\r\n" +
        "Transport: RTP/AVP/$params\r\n" +
        addHeaders() + "\r\n"
    Log.i(TAG, setup)
    return setup
  }

  fun createRecord(): String {
    val record = "RECORD rtsp://$host:$port$path RTSP/1.0\r\n" +
        "Range: npt=0.000-\r\n" + addHeaders() + "\r\n"
    Log.i(TAG, record)
    return record
  }

  fun createAnnounce(): String {
    val body = createBody()
    val announce = "ANNOUNCE rtsp://$host:$port$path RTSP/1.0\r\n" +
        "Content-Type: application/sdp\r\n" +
        addHeaders() +
        "Content-Length: ${body.length}\r\n\r\n" +
        body
    Log.i(TAG, announce)
    return announce
  }

  fun createAnnounceWithAuth(authResponse: String): String {
    authorization = createAuth(authResponse)
    Log.i("Auth", "$authorization")
    return createAnnounce()
  }

  fun createTeardown(): String {
    val teardown = "TEARDOWN rtsp://$host:$port$path RTSP/1.0\r\n" + addHeaders() + "\r\n"
    Log.i(TAG, teardown)
    return teardown
  }

  @Throws(IOException::class)
  fun getResponse(reader: BufferedReader, method: Method = Method.UNKNOWN): Command {
    var response = ""
    var line: String?
    while (reader.readLine().also { line = it } != null) {
      response += "${line ?: ""}\n"
      //end of response
      if ((line?.length ?: 0) < 3) break
    }
    Log.i(TAG, response)
    return if (method == Method.UNKNOWN) {
      commandParser.parseCommand(response)
    } else {
      val command = commandParser.parseResponse(method, response)
      sessionId = commandParser.getSessionId(command)
      if (command.method == Method.SETUP && protocol == Protocol.UDP) {
        commandParser.loadServerPorts(command, protocol, audioClientPorts, videoClientPorts,
          audioServerPorts, videoServerPorts)
      }
      command
    }
  }

  //Unused commands
  fun createPause(): String {
    return ""
  }

  fun createPlay(): String {
    return ""
  }

  fun createGetParameter(): String {
    return ""
  }

  fun createSetParameter(): String {
    return ""
  }

  fun createRedirect(): String {
    return ""
  }
}