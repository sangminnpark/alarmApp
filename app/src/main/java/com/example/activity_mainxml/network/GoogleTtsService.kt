package com.example.activity_mainxml.network


import com.example.activity_mainxml.model.TtsModel
import com.example.activity_mainxml.model.TtsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleTtsService {
    @POST("v1/text:synthesize")
    suspend fun synthesizeText(
        @Query("key") apiKey: String,
        @Body request: TtsModel
    ): Response<TtsResponse>
}