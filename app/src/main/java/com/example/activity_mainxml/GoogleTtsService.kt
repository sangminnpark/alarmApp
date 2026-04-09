package com.example.activity_mainxml

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleTtsService {
    @POST("v1/text:synthesize")
    suspend fun synthesize(
        @Query("key") apiKey: String,
        @Body request: TtsRequest
    ): TtsResponse
}