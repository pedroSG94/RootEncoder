package com.pedro.rtsp.rtsp.commands

import android.util.Base64
import android.util.Log
import com.pedro.rtsp.BuildConfig
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.commands.SdpBody.createAacBody
import com.pedro.rtsp.rtsp.commands.SdpBody.createH264Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH265Body
import com.pedro.rtsp.utils.AuthUtil.getMd5Hash
import com.pedro.rtsp.utils.RtpConstants
import java.io.BufferedReader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 * Created by pedro on 12/02/19.
 *
 * Class to create request to server and parse response from server.
 */
open class CommandsManager() {

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
  var isOnlyAudio = false

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
    val uptime = System.currentTimeMillis()
    timeStamp = uptime / 1000 shl 32 and ((uptime - uptime / 1000 * 1000 shr 32)
        / 1000) // NTP timestamp
  }

  private fun getData(byteBuffer: ByteBuffer?): ByteArray? {
    return if (byteBuffer != null) {
      val bytes = ByteArray(byteBuffer.capacity() - 4)
      byteBuffer.position(4)
      byteBuffer[bytes, 0, bytes.size]
      bytes
    } else {
      null
    }
  }

  private fun encodeToString(bytes: ByteArray?): String {
    bytes?.let {
      return Base64.encodeToString(it, 0, it.size, Base64.NO_WRAP)
    }
    return ""
  }

  fun setVideoInfo(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {
    this.sps = getData(sps)
    this.pps = getData(pps)
    this.vps = getData(vps) //H264 has no vps so if not null assume H265
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
    get() = encodeToString(sps)
  private val ppsString: String
    get() = encodeToString(pps)
  private val vpsString: String
    get() = encodeToString(vps)

  private fun addHeaders(): String {
    return "CSeq: ${++cSeq}\r\n" +
        "User-Agent: ${BuildConfig.LIBRARY_PACKAGE_NAME} ${BuildConfig.VERSION_NAME}\r\n" +
        (if (sessionId == null) "" else "Session: $sessionId\r\n") +
        (if (authorization == null) "" else "Authorization: $authorization\r\n")
  }

  private fun createBody(): String {
    var videoBody = ""
    if (!isOnlyAudio) {
      videoBody = if (vps == null) {
        createH264Body(RtpConstants.trackVideo, spsString, ppsString)
      } else {
        createH265Body(RtpConstants.trackVideo, spsString, ppsString, vpsString)
      }
    }
    return "v=0\r\n" +
        "o=- $timeStamp $timeStamp IN IP4 127.0.0.1\r\n" +
        "s=Unnamed\r\n" +
        "i=N/A\r\n" +
        "c=IN IP4 $host\r\n" +
        "t=0 0\r\n" +
        "a=recvonly\r\n" +
        videoBody +
        createAacBody(RtpConstants.trackAudio, sampleRate, isStereo)
  }

  private fun createAuth(authResponse: String): String {
    val authPattern = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"", Pattern.CASE_INSENSITIVE)
    val matcher = authPattern.matcher(authResponse)
    //digest auth
    return if (matcher.find()) {
      Log.i(TAG, "using digest auth")
      val realm = matcher.group(1)
      val nonce = matcher.group(2)
      val hash1 = getMd5Hash("$user:$realm:$password")
      val hash2 = getMd5Hash("ANNOUNCE:rtsp://$host:$port$path")
      val hash3 = getMd5Hash("$hash1:$nonce:$hash2")
      "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"rtsp://$host:$port$path\", response=\"$hash3\""
      //basic auth
    } else {
      Log.i(TAG, "using basic auth")
      val data = "$user:$password"
      val base64Data = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
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

  //Response parser
  fun getResponse(reader: BufferedReader?, method: Method = Method.UNKNOWN): Command {
    reader?.let { br ->
      return try {
        var response = ""
        var line: String?
        while (br.readLine().also { line = it } != null) {
          response += "${line ?: ""}\n"
          //end of response
          if (line?.length ?: 0 < 3) break
        }
        Log.i(TAG, response)
        if (method == Method.UNKNOWN) {
          Command.parseCommand(response)
        } else {
          val command = Command.parseResponse(method, response)
          getSession(command.text)
          if (command.method == Method.SETUP && protocol == Protocol.UDP) {
            getServerPorts(command.text)
          }
          command
        }
      } catch (e: IOException) {
        Log.e(TAG, "read error", e)
        Command(method, cSeq, -1, "")
      }
    }
    return Command(method, cSeq, -1, "")
  }

  private fun getSession(response: String) {
    val rtspPattern = Pattern.compile("Session:(\\s?\\w+)")
    val matcher = rtspPattern.matcher(response)
    if (matcher.find()) {
      sessionId = matcher.group(1) ?: ""
      sessionId?.let {
        val temp = it.split(";")[0]
        sessionId = temp.trim()
      }
    }
  }

  private fun getServerPorts(response: String) {
    var isAudio = true
    val clientPattern = Pattern.compile("client_port=([0-9]+)-([0-9]+)")
    val clientMatcher = clientPattern.matcher(response)
    if (clientMatcher.find()) {
      val port = (clientMatcher.group(1) ?: "-1").toInt()
      isAudio = port == audioClientPorts[0]
    }

    val rtspPattern = Pattern.compile("server_port=([0-9]+)-([0-9]+)")
    val matcher = rtspPattern.matcher(response)
    if (matcher.find()) {
      if (isAudio) {
        audioServerPorts[0] = (matcher.group(1) ?: "${audioClientPorts[0]}").toInt()
        audioServerPorts[1] = (matcher.group(2) ?: "${audioClientPorts[1]}").toInt()
      } else {
        videoServerPorts[0] = (matcher.group(1) ?: "${videoClientPorts[0]}").toInt()
        videoServerPorts[1] = (matcher.group(2) ?: "${videoClientPorts[1]}").toInt()
      }
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