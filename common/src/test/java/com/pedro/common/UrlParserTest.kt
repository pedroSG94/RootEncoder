package com.pedro.common

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * Created by pedro on 2/9/24.
 */
class UrlParserTest {

  @Test
  fun testRtmpUrls() {
    try {
      val url = "rtmp://localhost:1935/live?test/fake"
      val urlParser = UrlParser.parse(url, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser.scheme)
      assertEquals("localhost", urlParser.host)
      assertEquals(1935, urlParser.port)
      assertEquals("live?test", urlParser.getAppName())
      assertEquals("fake", urlParser.getStreamName())
      assertEquals("rtmp://localhost:1935/live?test", urlParser.getTcUrl())

      val url0 = "rtmp://192.168.238.182:1935/live/100044?userId=100044&roomTitle=123123&roomCover=http://192.168.238.182/xxxx.png"
      val urlParser0 = UrlParser.parse(url0, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser0.scheme)
      assertEquals("192.168.238.182", urlParser0.host)
      assertEquals(1935, urlParser0.port)
      assertEquals("live", urlParser0.getAppName())
      assertEquals("100044?userId=100044&roomTitle=123123&roomCover=http://192.168.238.182/xxxx.png", urlParser0.getStreamName())
      assertEquals("rtmp://192.168.238.182:1935/live", urlParser0.getTcUrl())

      val url1 = "rtmp://user:pass@localhost:1935/live?test/fake"
      val urlParser1 = UrlParser.parse(url1, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser1.scheme)
      assertEquals("localhost", urlParser1.host)
      assertEquals(1935, urlParser1.port)
      assertEquals("live?test", urlParser1.getAppName())
      assertEquals("fake", urlParser1.getStreamName())
      assertEquals("rtmp://localhost:1935/live?test", urlParser1.getTcUrl())
      assertEquals("user", urlParser1.getAuthUser())
      assertEquals("pass", urlParser1.getAuthPassword())

      val url2 = "rtmps://192.168.0.1/live"
      val urlParser2 = UrlParser.parse(url2, arrayOf("rtmps"))
      assertEquals("rtmps", urlParser2.scheme)
      assertEquals("192.168.0.1", urlParser2.host)
      assertEquals(null, urlParser2.port)
      assertEquals("live", urlParser2.getAppName())
      assertEquals("", urlParser2.getStreamName())
      assertEquals("rtmps://192.168.0.1/live", urlParser2.getTcUrl())

      val url3 = "rtmpts://192.168.0.1:1234/live/test"
      val urlParser3 = UrlParser.parse(url3, arrayOf("rtmpts"))
      assertEquals("rtmpts", urlParser3.scheme)
      assertEquals("192.168.0.1", urlParser3.host)
      assertEquals(1234, urlParser3.port)
      assertEquals("live", urlParser3.getAppName())
      assertEquals("test", urlParser3.getStreamName())
      assertEquals("rtmpts://192.168.0.1:1234/live", urlParser3.getTcUrl())

      val url4 = "rtmp://192.168.0.1:1234/live/"
      val urlParser4 = UrlParser.parse(url4, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser4.scheme)
      assertEquals("192.168.0.1", urlParser4.host)
      assertEquals(1234, urlParser4.port)
      assertEquals("live", urlParser4.getAppName())
      assertEquals("", urlParser4.getStreamName())
      assertEquals("rtmp://192.168.0.1:1234/live", urlParser4.getTcUrl())

      val url5 = "rtmp://192.168.0.1:1234?live"
      val urlParser5 = UrlParser.parse(url5, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser5.scheme)
      assertEquals("192.168.0.1", urlParser5.host)
      assertEquals(1234, urlParser5.port)
      assertEquals("live", urlParser5.getAppName())
      assertEquals("", urlParser5.getStreamName())
      assertEquals("rtmp://192.168.0.1:1234/live", urlParser5.getTcUrl())

      val url6 = "rtmp://192.168.0.1:1234/live/test/fake"
      val urlParser6 = UrlParser.parse(url6, arrayOf("rtmp"))
      assertEquals("rtmp", urlParser6.scheme)
      assertEquals("192.168.0.1", urlParser6.host)
      assertEquals(1234, urlParser6.port)
      assertEquals("live/test", urlParser6.getAppName())
      assertEquals("fake", urlParser6.getStreamName())
      assertEquals("rtmp://192.168.0.1:1234/live/test", urlParser6.getTcUrl())
    } catch (e: URISyntaxException) {
      assert(false)
    }
  }

  @Test
  fun testRtspUrls() {
    try {
      val url = "rtsp://localhost:1935/live?test/fake"
      val urlParser = UrlParser.parse(url, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser.scheme)
      assertEquals("localhost", urlParser.host)
      assertEquals(1935, urlParser.port)
      assertEquals("live?test/fake", urlParser.getFullPath())

      val url1 = "rtsp://user:pass@localhost:1935/live?test/fake"
      val urlParser1 = UrlParser.parse(url1, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser1.scheme)
      assertEquals("localhost", urlParser1.host)
      assertEquals(1935, urlParser1.port)
      assertEquals("live?test/fake", urlParser1.getFullPath())
      assertEquals("user", urlParser1.getAuthUser())
      assertEquals("pass", urlParser1.getAuthPassword())

      val url2 = "rtsps://192.168.0.1/live"
      val urlParser2 = UrlParser.parse(url2, arrayOf("rtsps"))
      assertEquals("rtsps", urlParser2.scheme)
      assertEquals("192.168.0.1", urlParser2.host)
      assertEquals(null, urlParser2.port)
      assertEquals("live", urlParser2.getFullPath())

      val url3 = "rtsp://192.168.0.1:1234/live/test"
      val urlParser3 = UrlParser.parse(url3, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser3.scheme)
      assertEquals("192.168.0.1", urlParser3.host)
      assertEquals(1234, urlParser3.port)
      assertEquals("live/test", urlParser3.getFullPath())

      val url4 = "rtsp://192.168.0.1:1234/live/"
      val urlParser4 = UrlParser.parse(url4, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser4.scheme)
      assertEquals("192.168.0.1", urlParser4.host)
      assertEquals(1234, urlParser4.port)
      assertEquals("live/", urlParser4.getFullPath())

      val url5 = "rtsp://192.168.0.1:1234?live"
      val urlParser5 = UrlParser.parse(url5, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser5.scheme)
      assertEquals("192.168.0.1", urlParser5.host)
      assertEquals(1234, urlParser5.port)
      assertEquals("live", urlParser5.getFullPath())

      val url6 = "rtsp://192.168.0.1:1234/live/test/fake"
      val urlParser6 = UrlParser.parse(url6, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser6.scheme)
      assertEquals("192.168.0.1", urlParser6.host)
      assertEquals(1234, urlParser6.port)
      assertEquals("live/test/fake", urlParser6.getFullPath())
    } catch (e: URISyntaxException) {
      assert(false)
    }
  }

  @Test
  fun testSrtUrls() {
    try {
      val url = "srt://localhost:1935/live?test/fake"
      val urlParser = UrlParser.parse(url, arrayOf("srt"))
      assertEquals("srt", urlParser.scheme)
      assertEquals("localhost", urlParser.host)
      assertEquals(1935, urlParser.port)
      assertEquals(null, urlParser.getQuery("streamid"))
      assertEquals("live?test/fake", urlParser.getFullPath())

      val url2 = "srt://192.168.0.1?streamid=test/fake"
      val urlParser2 = UrlParser.parse(url2, arrayOf("srt"))
      assertEquals("srt", urlParser2.scheme)
      assertEquals("192.168.0.1", urlParser2.host)
      assertEquals(null, urlParser2.port)
      assertEquals("test/fake", urlParser2.getQuery("streamid"))

      val url3 = "srt://192.168.0.1:1234?live=test&streamid=fake"
      val urlParser3 = UrlParser.parse(url3, arrayOf("srt"))
      assertEquals("srt", urlParser3.scheme)
      assertEquals("192.168.0.1", urlParser3.host)
      assertEquals(1234, urlParser3.port)
      assertEquals("fake", urlParser3.getQuery("streamid"))

      val url4 = "rtsp://192.168.0.1:1234/streamid-test"
      val urlParser4 = UrlParser.parse(url4, arrayOf("rtsp"))
      assertEquals("rtsp", urlParser4.scheme)
      assertEquals("192.168.0.1", urlParser4.host)
      assertEquals(1234, urlParser4.port)
      assertEquals(null, urlParser4.getQuery("streamid"))
      assertEquals("streamid-test", urlParser4.getFullPath())

      val url5 = "srt://push.domain.com:1105?streamid=#!::h=push.domain.com,r=/live/stream,m=publish"
      val urlParser5 = UrlParser.parse(url5, arrayOf("srt"))
      assertEquals("srt", urlParser5.scheme)
      assertEquals("push.domain.com", urlParser5.host)
      assertEquals(1105, urlParser5.port)
      assertEquals("#!::h=push.domain.com,r=/live/stream,m=publish", urlParser5.getQuery("streamid"))

      val url6 = "srt://push.domain.com:1105/#!::h=push.domain.com,r=/live/stream,m=publish"
      val urlParser6 = UrlParser.parse(url6, arrayOf("srt"))
      assertEquals("srt", urlParser6.scheme)
      assertEquals("push.domain.com", urlParser6.host)
      assertEquals(1105, urlParser6.port)
      assertEquals(null, urlParser6.getQuery("streamid"))
      assertEquals("#!::h=push.domain.com,r=/live/stream,m=publish", urlParser6.getFullPath())
    } catch (e: URISyntaxException) {
      assert(false)
    }
  }

  @Test
  fun testUdpUrls() {
    try {
      val url = "udp://localhost:1935"
      val urlParser = UrlParser.parse(url, arrayOf("udp"))
      assertEquals("udp", urlParser.scheme)
      assertEquals("localhost", urlParser.host)
      assertEquals(1935, urlParser.port)

      val url0 = "udp://localhost:1935/"
      val urlParser0 = UrlParser.parse(url0, arrayOf("udp"))
      assertEquals("udp", urlParser0.scheme)
      assertEquals("localhost", urlParser0.host)
      assertEquals(1935, urlParser0.port)
    } catch (e: URISyntaxException) {
      assert(false)
    }
  }

  @Test
  fun testUrl2() {
    try {
      val urlParser = UrlParser.parse(
        "srt://push.domain.com:1105?streamid=#!::h=push.domain.com,r=/live/stream,m=publish&live=asd",
        arrayOf("srt")
      )
      println(urlParser.toString())
      println(urlParser.getQuery("streamid"))
    } catch (e: IllegalArgumentException) {

    }
  }

  @Test
  fun testUrl3() {
    try {
      val urlParser = UrlParser.parse(
        "rtmp://localhost?live=adasdasd",
        arrayOf("rtmp")
      )
      println(urlParser.toString())
    } catch (e: IllegalArgumentException) {

    }
  }

  private fun oldParser(url: String) {
    val urlPattern: Pattern = Pattern.compile("^rtmpt?s?://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$")
    val tunneled: Boolean
    val tlsEnabled: Boolean
    val rtmpMatcher = urlPattern.matcher(url)
    if (!rtmpMatcher.matches()) {
      assert(false)
    }
    val schema = rtmpMatcher.group(0) ?: ""
    tunneled = schema.startsWith("rtmpt")
    tlsEnabled = schema.startsWith("rtmps") || schema.startsWith("rtmpts")

    val host = rtmpMatcher.group(1) ?: ""
    val portStr = rtmpMatcher.group(2)
    val defaultPort = if (tlsEnabled) 443 else if (tunneled) 80 else 1935
    val port = portStr?.toInt() ?: defaultPort
    val appName = getAppName(rtmpMatcher.group(3) ?: "", rtmpMatcher.group(4) ?: "")
    val streamName = getStreamName(rtmpMatcher.group(4) ?: "")
    val tcUrl = getTcUrl((rtmpMatcher.group(0)
      ?: "").substring(0, (rtmpMatcher.group(0)
      ?: "").length - streamName.length))
    println("OldParser(scheme='$schema', host='$host', port=$port, appName='$appName', streamName='$streamName', tcUrl='$tcUrl'")
  }

  private fun getAppName(app: String, name: String): String {
    return if (!name.contains("/")) {
      app
    } else {
      app + "/" + name.substring(0, name.indexOf("/"))
    }
  }

  private fun getStreamName(name: String): String {
    return if (!name.contains("/")) {
      name
    } else {
      name.substring(name.indexOf("/") + 1)
    }
  }

  private fun getTcUrl(url: String): String {
    return if (url.endsWith("/")) {
      url.substring(0, url.length - 1)
    } else {
      url
    }
  }
}