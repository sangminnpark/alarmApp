package com.example.activity_mainxml.network

import ElevenLabsApi
import com.example.activity_mainxml.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // API KEYS
    private const val GOOGLE_API_KEY = BuildConfig.GOOGLE_API_KEY
    private const val ELEVENLABS_API_KEY = BuildConfig.ELEVEN_LABS_API_KEY

    // BASE URLS
    private const val GOOGLE_BASE_URL = "https://texttospeech.googleapis.com/"
    private const val ELEVENLABS_BASE_URL = "https://api.elevenlabs.io/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 1. 구글 TTS 서비스
    val googleTtsService: GoogleTtsService by lazy {
        Retrofit.Builder()
            .baseUrl(GOOGLE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleTtsService::class.java)
    }

    // 2. ElevenLabs API 서비스
    val elevenLabsApi: ElevenLabsApi by lazy {
        Retrofit.Builder()
            .baseUrl(ELEVENLABS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ElevenLabsApi::class.java)
    }
}