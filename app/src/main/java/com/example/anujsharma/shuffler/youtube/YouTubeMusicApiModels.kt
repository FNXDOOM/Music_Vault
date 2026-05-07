package com.example.anujsharma.shuffler.youtube

data class YouTubeSong(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val album: String? = null
)

data class YouTubePlaylist(
    val id: String,
    val title: String,
    val artworkUrl: String? = null,
    val songs: List<YouTubeSong> = emptyList()
)

data class YouTubeArtist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null
)

data class SearchResponse(
    val songs: List<YouTubeSong> = emptyList(),
    val playlists: List<YouTubePlaylist> = emptyList(),
    val artists: List<YouTubeArtist> = emptyList()
)

data class StreamResponse(
    val url: String,
    val expiresAt: Long
)
