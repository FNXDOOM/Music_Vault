package com.example.anujsharma.shuffler.youtube

import com.example.anujsharma.shuffler.volley.Urls
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object YouTubeMusicApiClient {
    val service: YouTubeMusicApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Urls.YOUTUBE_BACKEND_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeMusicApiService::class.java)
    }
}
