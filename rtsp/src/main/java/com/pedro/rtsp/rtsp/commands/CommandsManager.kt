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
import com.pedro.common.getData
import com.pedro.common.getMd5Hash
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.commands.SdpBody.createAV1Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createAacBody
import com.pedro.rtsp.rtsp.commands.SdpBody.createG711Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH264Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH265Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createOpusBody
import com.pedro.rtsp.rtsp.commands.SdpBody.createVp8Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createVp9Body
import com.pedro.rtsp.utils.RtpTracks
import com.pedro.rtsp.utils.encodeToString
import java.io.IOException
import java.net.DatagramSocket
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
  private val urlHost: String
    get() = host?.let { if (it.contains(":")) "[$it]" else it } ?: ""
  var sps: ByteBuffer? = null
    private set
  var pps: ByteBuffer? = null
    private set
  var vps: ByteBuffer? = null
    private set
  private var cSeq = 0
  private var sessionId: String? = null
  private var timeStamp = 0L
  var sampleRate = 32000
  var isStereo = true
  var protocol: Protocol = Protocol.TCP
  var videoDisabled = false
  var audioDisabled = false
  private val commandParser = CommandParser()
  val rtpTracks = RtpTracks()
  var videoCodec = VideoCodec.H264
  var audioCodec = AudioCodec.AAC
  //For udp
  val audioClientPorts = arrayOf<Int?>(5000, 5001)
  val videoClientPorts = arrayOf<Int?>(5002, 5003)
  val audioServerPorts = arrayOf<Int?>(5004, 5005)
  val videoServerPorts = arrayOf<Int?>(5006, 5007)

  val spsString: String
    get() = sps?.getData()?.encodeToString() ?: ""
  val ppsString: String
    get() = pps?.getData()?.encodeToString() ?: ""
  val vpsString: String
    get() = vps?.getData()?.encodeToString() ?: ""

  //For auth
  var user: String? = null
    private set
  var password: String? = null
    private set
  private var shouldSendAuth = false
  private var realm: String? = null
  private var nonce: String? = null

  companion object {
    private const val TAG = "CommandsManager"
  }

  fun videoInfoReady(): Boolean {
    return when (videoCodec) {
      VideoCodec.H264 -> sps != null && pps != null
      VideoCodec.H265 -> sps != null && pps != null && vps != null
      VideoCodec.AV1 -> sps != null
      VideoCodec.VP8, VideoCodec.VP9 -> sps != null
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    this.sps = sps
    this.pps = pps
    this.vps = vps
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
    shouldSendAuth = false
    realm = null
    nonce = null
  }

  private fun addHeaders(method: Method, uri: String): String {
    val user = this.user
    val password = this.password
    return "CSeq: ${++cSeq}\r\n" +
        (if (sessionId.isNullOrEmpty()) "" else "Session: $sessionId\r\n") +
        (if (shouldSendAuth && !user.isNullOrEmpty() && !password.isNullOrEmpty()) {
          "Authorization: ${createAuth(user, password, method, uri, realm, nonce)}\r\n"
        } else "")
  }

  private fun createBody(): String {
    var videoBody = ""
    if (!videoDisabled) {
      videoBody = when (videoCodec) {
        VideoCodec.H264 -> createH264Body(rtpTracks.trackVideo, spsString, ppsString)
        VideoCodec.H265 -> createH265Body(rtpTracks.trackVideo, spsString, ppsString, vpsString)
        VideoCodec.VP8 -> createVp8Body(rtpTracks.trackVideo)
        VideoCodec.VP9 -> createVp9Body(rtpTracks.trackVideo)
        VideoCodec.AV1 -> createAV1Body(rtpTracks.trackVideo)
      }
    }
    var audioBody = ""
    if (!audioDisabled) {
      audioBody = when (audioCodec) {
        AudioCodec.G711 -> createG711Body(rtpTracks.trackAudio, sampleRate, isStereo)
        AudioCodec.AAC -> createAacBody(rtpTracks.trackAudio, sampleRate, isStereo)
        AudioCodec.OPUS -> createOpusBody(rtpTracks.trackAudio)
      }
    }
    val isIpv6 = host?.contains(":") == true
    val addressType = if (isIpv6) "IP6" else "IP4"
    val localhost = if (isIpv6) "::1" else "127.0.0.1"
    return "v=0\r\n" +
        "o=- $timeStamp $timeStamp IN $addressType $localhost\r\n" +
        "s=Unnamed\r\n" +
        "i=N/A\r\n" +
        "c=IN $addressType $host\r\n" +
        "t=0 0\r\n" +
        "a=recvonly\r\n" +
        videoBody + audioBody
  }

  private fun createAuth(user: String, password: String, method: Method, uri: String, realm: String?, nonce: String?): String {
    //digest auth
    return if (realm != null && nonce != null) {
      Log.i(TAG, "using digest auth")
      val hash1 = "$user:$realm:$password".getMd5Hash()
      val hash2 = "${method.name}:$uri".getMd5Hash()
      val hash3 = "$hash1:$nonce:$hash2".getMd5Hash()
      "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$hash3\""
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
    val uri = "rtsp://$urlHost:$port$path"
    val options = "OPTIONS $uri RTSP/1.0\r\n" + addHeaders(Method.OPTIONS, uri) + "\r\n"
    Log.i(TAG, options)
    return options
  }

  open fun createSetup(track: Int): String {
    val udpPorts = if (track == rtpTracks.trackVideo) videoClientPorts else audioClientPorts
    val params = if (protocol === Protocol.UDP) {
      "UDP;unicast;client_port=${udpPorts[0]}-${udpPorts[1]};mode=record"
    } else {
      "TCP;unicast;interleaved=${2 * track}-${2 * track + 1};mode=record"
    }
    val uri = "rtsp://$urlHost:$port$path/streamid=$track"
    val setup = "SETUP $uri RTSP/1.0\r\n" +
        "Transport: RTP/AVP/$params\r\n" +
        addHeaders(Method.SETUP, uri) + "\r\n"
    Log.i(TAG, setup)
    return setup
  }

  fun createRecord(): String {
    val uri = "rtsp://$urlHost:$port$path"
    val record = "RECORD $uri RTSP/1.0\r\n" +
        "Range: npt=0.000-\r\n" + addHeaders(Method.RECORD, uri) + "\r\n"
    Log.i(TAG, record)
    return record
  }

  fun createAnnounce(): String {
    val body = createBody()
    val uri = "rtsp://$urlHost:$port$path"
    val announce = "ANNOUNCE $uri RTSP/1.0\r\n" +
        "Content-Type: application/sdp\r\n" +
        addHeaders(Method.ANNOUNCE, uri) +
        "Content-Length: ${body.length}\r\n\r\n" +
        body
    Log.i(TAG, announce)
    return announce
  }

  fun createAnnounceWithAuth(authResponse: String): String {
    val realmMatcher = Pattern.compile("realm=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(authResponse)
    val nonceMatcher = Pattern.compile("nonce=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(authResponse)
    if (realmMatcher.find() && nonceMatcher.find()) {
      this.realm = realmMatcher.group(1)
      this.nonce = nonceMatcher.group(1)
    }
    shouldSendAuth = true
    return createAnnounce()
  }

  fun createTeardown(): String {
    val uri = "rtsp://$urlHost:$port$path"
    val teardown = "TEARDOWN $uri RTSP/1.0\r\n" + addHeaders(Method.TEARDOWN, uri) + "\r\n"
    Log.i(TAG, teardown)
    return teardown
  }

  @Throws(IOException::class)
  suspend fun getResponse(socket: TcpStreamSocket, method: Method = Method.UNKNOWN, track: Int? = null): Command {
    var response = ""
    var line: String?
    while (socket.readLine().also { line = it } != null) {
      response += "${line ?: ""}\n"
      if ((line?.length ?: 0) < 3) break
    }
    val contentLength = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
      .find(response)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    if (contentLength > 0) response += socket.read(contentLength).decodeToString()
    Log.i(TAG, response)
    return if (method == Method.UNKNOWN) {
      commandParser.parseCommand(response)
    } else {
      val command = commandParser.parseResponse(method, response)
      sessionId = commandParser.getSessionId(command)
      if (command.method == Method.SETUP && protocol == Protocol.UDP) {
        val isAudio = track == rtpTracks.trackAudio
        commandParser.loadServerPorts(command, protocol, isAudio, audioClientPorts, videoClientPorts,
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

  fun updateTimestamp() {
    timeStamp = TimeUtils.getCurrentTimeNano()
  }

  fun findFreeClientPorts(): Boolean {
    return runCatching {
      val freePorts = findFreeUdpPortPairs()
      videoClientPorts[0] = freePorts[0]
      videoClientPorts[1] = freePorts[1]
      audioClientPorts[0] = freePorts[2]
      audioClientPorts[1] = freePorts[3]
      true
    }.getOrDefault(false)
  }

  fun findFreeServerPorts(): Boolean {
    return runCatching {
      val freePorts = findFreeUdpPortPairs()
      videoServerPorts[0] = freePorts[0]
      videoServerPorts[1] = freePorts[1]
      audioServerPorts[0] = freePorts[2]
      audioServerPorts[1] = freePorts[3]
      true
    }.getOrDefault(false)
  }

  @Throws(IOException::class)
  private fun findFreeUdpPortPairs(): IntArray {
    val reservedSockets = mutableListOf<DatagramSocket>()
    try {
      val videoPort = reservePortPair(reservedSockets)
      val audioPort = reservePortPair(reservedSockets)
      return intArrayOf(videoPort, videoPort + 1, audioPort, audioPort + 1)
    } finally {
      reservedSockets.forEach { runCatching { it.close() } }
    }
  }

  //RTP must use an even port and RTCP the next odd port (RFC 3550)
  @Throws(IOException::class)
  private fun reservePortPair(reservedSockets: MutableList<DatagramSocket>): Int {
    repeat(100) {
      val seed = DatagramSocket(0)
      val candidate = seed.localPort.let { if (it % 2 == 0) it else it + 1 }
      seed.close()
      if (candidate >= 65535) return@repeat
      try {
        reservedSockets.add(DatagramSocket(candidate))
        reservedSockets.add(DatagramSocket(candidate + 1))
        return candidate
      } catch (_: IOException) { }
    }
    throw IOException("No free UDP port pair available")
  }
}