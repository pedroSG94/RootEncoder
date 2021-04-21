package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.RtmpMessage
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
class CommandsManager {

  private val TAG = "CommandsManager"

  private var messageStreamId = 0
  var host = ""
  var port = 1935
  var appName = ""
  var streamName = ""
  var  tcUrl = ""
  private var user: String? = null
  private var password: String? = null

  fun setAuth(user: String?, password: String?) {
    this.user = user
    this.password = password
  }

  @Throws(IOException::class)
  fun sendConnect(auth: String, output: OutputStream) {
    val connect = CommandAmf0("connect")
    val connectInfo = AmfObject()
    connectInfo.setProperty("app", appName + auth)
    connectInfo.setProperty("flashVer", "FMLE/3.0 (compatible; Lavf57.56.101)")
    connectInfo.setProperty("swfUrl", "")
    connectInfo.setProperty("tcUrl", tcUrl + auth)
    connectInfo.setProperty("fpad", false)
    connectInfo.setProperty("capabilities", 239.0)
    connectInfo.setProperty("audioCodecs", 3191.0)
    connectInfo.setProperty("videoCodecs", 252.0)
    connectInfo.setProperty("videoFunction", 1.0)
    connectInfo.setProperty("pageUrl", "")
    connectInfo.setProperty("objectEncoding", 0.0)
    connect.addData(connectInfo)

    connect.writeHeader(output)
    connect.writeBody(output)
    Log.i(TAG, "$connect")
  }

  @Throws(IOException::class)
  fun readMessageResponse(input: InputStream): RtmpMessage {
    val message = RtmpMessage.getRtmpMessage(input)
    Log.i(TAG, message.toString())
    return message
  }

  fun reset() {
    messageStreamId = 0
  }
}