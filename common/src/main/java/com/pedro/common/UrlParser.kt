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
      val uri = URI(endpoint)
      if (!requiredProtocol.contains(uri.scheme.trim())) {
        throw URISyntaxException(endpoint, "Invalid protocol: ${uri.scheme}")
      }
      if (uri.userInfo != null && !uri.userInfo.contains(":")) {
        throw URISyntaxException(endpoint, "Invalid auth. Auth must contain ':'")
      }
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

  init {
    val url = uri.toString()
    scheme = uri.scheme
    host = uri.host
    port = if (uri.port < 0) null else uri.port
    path = uri.path.removePrefix("/")
    if (uri.query != null) {
      val i = url.indexOf(uri.query)
      query = url.substring(if (i < 0) 0 else i)
    }
    auth = uri.userInfo
  }

  fun getQuery(key: String): String? = getAllQueries()[key]

  fun getAuthUser(): String? {
    val userInfo = auth?.split(":") ?: return null
    return if (userInfo.size == 2) userInfo[0] else null
  }

  fun getAuthPassword(): String? {
    val userInfo = auth?.split(":") ?: return null
    return if (userInfo.size == 2) userInfo[1] else null
  }

  fun getAppName(): String {
    val fullPath = getFullPath()
    val indexes = fullPath.getIndexes('/')
    return when (indexes.size) {
      0 -> fullPath
      1 -> fullPath.substring(0, indexes[0])
      else -> {
        if (getAllQueries().isEmpty()) {
          fullPath.substring(0, indexes[1])
        } else {
          fullPath.substring(0, indexes[0])
        }
      }
    }
  }

  fun getStreamName(): String = getFullPath().removePrefix(getAppName()).removePrefix("/")

  fun getTcUrl(): String {
    val port = if (port != null) ":$port" else ""
    val appName = if (getAppName().isNotEmpty()) "/${getAppName()}" else ""
    return "$scheme://$host${port}${appName}"
  }

  fun getFullPath(): String {
    val fullPath = "$path${if (query == null) "" else "?$query"}".removePrefix("?")
    if (fullPath.isEmpty()) {
      val port = if (port != null) ":$port" else ""
      return url.removePrefix("$scheme://$host$port").removePrefix("/")
    }
    return fullPath
  }

  private fun getAllQueries(): Map<String, String> {
    val queries = query?.split("&") ?: emptyList()
    val map = HashMap<String, String>()
    queries.forEach { entry ->
      val data = entry.split(Pattern.compile("="), 2)
      if (data.size == 2) map[data[0]] = data[1]
    }
    return map
  }
}