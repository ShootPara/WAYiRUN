package com.example.runningapp.tracking

import org.junit.Assert.*
import org.junit.Test

class PlaylistLinkTest {
    @Test fun sharedPlaylistIsCanonicalAndTrackingParametersAreRemoved() {
        assertEquals("https://music.youtube.com/playlist?list=PL_example-123",
            playlistLink(" https://music.youtube.com/playlist?list=PL_example-123&si=tracking "))
    }
    @Test fun youtubePlaylistOpensInMusic() {
        assertEquals("https://music.youtube.com/playlist?list=PL123",
            playlistLink("https://www.youtube.com/playlist?list=PL123"))
    }
    @Test fun invalidOrAmbiguousLinksAreRejected() {
        listOf("", "not a link", "http://music.youtube.com/playlist?list=PL1",
            "https://music.youtube.com.evil.test/playlist?list=PL1",
            "https://user@music.youtube.com/playlist?list=PL1", "https://music.youtube.com:444/playlist?list=PL1",
            "https://music.youtube.com/watch?v=123", "https://music.youtube.com/playlist",
            "https://music.youtube.com/playlist?list=", "https://music.youtube.com/playlist?list=PL1&list=PL2",
            "https://music.youtube.com/playlist?list=%2Fbad", "https://music.youtube.com/playlist?list=%XX")
            .forEach { assertNull(it, playlistLink(it)) }
    }
}
