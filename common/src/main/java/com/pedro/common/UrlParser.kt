package com.pedro.common

import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * Created by pedro on 2/9/24.
 */
class UrlParser private constructor(
  uri: URI,
  private val url: String
) {

  companion object {
    @Throws(URISyntaxException::class)
    fun parse(endpoint: String, requiredProtocol: Array<String>): UrlParser {
      val uri = URI(endpoint.substringBefore("?"))
      if (uri.scheme != null && !requiredProtocol.contains(uri.scheme.trim())) {
        throw URISyntaxException(endpoint, "Invalid protocol: ${uri.scheme}")
      }
      if (uri.userInfo != null && !uri.userInfo.contains(":")) {
        throw URISyntaxException(endpoint, "Invalid auth. Auth must contain ':'")
      }
      if (uri.host == null) throw URISyntaxException(endpoint, "Invalid host: ${uri.host}")
      if (uri.rawPath == null) throw URISyntaxException(endpoint, "Invalid path: ${uri.rawPath}")
      return UrlParser(uri, endpoint)
    }
  }

  var scheme: String = ""
    private set
  var host: String = ""
    private set
  var port: Int? = null
    private set
  var path: String = ""
    private set
  var query: String? = null
    private set
  var auth: String? = null
    private set
  private var rawAuth: String? = null

  init {
    scheme = uri.scheme
    host = uri.host.removeSurrounding("[", "]")
    port = if (uri.port < 0) null else uri.port
    path = uri.rawPath.removePrefix("/")
    query = url.substringAfter("?", "").let { it.ifEmpty { null } }
    auth = uri.userInfo
    rawAuth = uri.rawUserInfo
  }

  fun getQuery(key: String): String? = getAllQueries()[key]

  fun getAuthUser(): String? {
    val userInfo = auth?.split(":", limit = 2) ?: return null
    return if (userInfo.size == 2) userInfo[0] else null
  }

  fun getAuthPassword(): String? {
    val userInfo = auth?.split(":", limit = 2) ?: return null
    return if (userInfo.size == 2) userInfo[1] else null
  }

  fun getAppName(): String {
    val queries = getAllQueries().map { (key, value) -> "$key=$value" }.joinToString("&")
    val fullPath = getFullPath().ifEmpty { query ?: "" }
    val path = fullPath.replace(queries, "")
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when(segments.size) {
      0 -> ""
      1 -> fullPath.trimEnd('/')
      2 -> segments[0]
      else -> segments.subList(0, 2).joinToString("/")
    }
  }

  fun getStreamName(): String = getFullPath().removePrefix(getAppName()).removePrefix("/").removePrefix("?")

  /**
   * Host ready to be concatenated in a url. IPv6 hosts need to be enclosed in brackets
   */
  fun getHostForUrl(): String = if (host.contains(":")) "[$host]" else host

  fun getTcUrl(): String {
    val port = if (port != null) ":$port" else ""
    val appName = if (getAppName().isNotEmpty()) "/${getAppName()}" else ""
    return "$scheme://${getHostForUrl()}${port}${appName}"
  }

  fun getFullPath(): String {
    val fullPath = "$path${if (query == null) "" else "?$query"}".removePrefix("?")
    if (fullPath.isEmpty()) {
      val port = if (port != null) ":$port" else ""
      val auth = if (rawAuth != null) "$rawAuth@" else ""
      return url.removePrefix("$scheme://$auth${getHostForUrl()}$port").removePrefix("/")
    }
    return fullPath
  }

  private fun getAllQueries(): Map<String, String> {
    val queries = query?.split("&") ?: emptyList()
    val map = LinkedHashMap<String, String>()
    queries.forEach { entry ->
      val data = entry.split(Pattern.compile("="), 2)
      if (data.size == 2) map[data[0]] = data[1]
    }
    return map
  }
}