package com.example.runningapp.tracking

import java.net.URI
import java.net.URLDecoder

/** Keep only the playlist identifier; discard share/tracking parameters. */
internal fun playlistLink(input: String): String? = try {
    val uri = URI(input.trim())
    val hosts = setOf("music.youtube.com", "youtube.com", "www.youtube.com")
    if (uri.scheme != "https" || uri.host?.lowercase() !in hosts || uri.userInfo != null ||
        uri.port != -1 || uri.path != "/playlist") null
    else {
        val ids = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .filter { URLDecoder.decode(it[0], "UTF-8") == "list" }
            .map { URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") }
        ids.singleOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,256}")) }
            ?.let { "https://music.youtube.com/playlist?list=$it" }
    }
} catch (_: IllegalArgumentException) { null }
catch (_: java.net.URISyntaxException) { null }
