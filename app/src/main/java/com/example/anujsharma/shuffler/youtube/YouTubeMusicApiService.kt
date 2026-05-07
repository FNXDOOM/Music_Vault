package com.example.anujsharma.shuffler.youtube

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YouTubeMusicApiService {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "song"
    ): SearchResponse

    @GET("api/song/{id}/stream")
    suspend fun getStreamUrl(@Path("id") songId: String): StreamResponse

    @GET("api/playlists")
    suspend fun getPlaylists(): List<YouTubePlaylist>

    @GET("api/playlist/{id}")
    suspend fun getPlaylist(@Path("id") playlistId: String): YouTubePlaylist
}
