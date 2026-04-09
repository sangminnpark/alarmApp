package com.example.activity_mainxml

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // : 을 . 으로 수정
import java.io.File
import java.io.FileOutputStream

object TtsManager {
    private const val BASE_URL = "https://texttospeech.googleapis.com/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val service by lazy {
        retrofit.create(GoogleTtsService::class.java)
    }

    // 수정된 부분: voiceName을 직접 파라미터로 받습니다.
    suspend fun fetchAndPlayVoice(
        context: Context,
        text: String,
        apiKey: String,
        voiceName: String // 파라미터 추가
    ) {
        val request = TtsRequest(
            input = TtsInput(text),
            voice = TtsVoice(name = voiceName), // 이제 밖에서 넘겨준 ID가 그대로 들어갑니다.
            audioConfig = TtsAudioConfig()
        )

        try {
            val response = service.synthesize(apiKey, request)
            val audioBytes = Base64.decode(response.audioContent, Base64.DEFAULT)

            val tempFile = File.createTempFile("google_tts_", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (tempFile.exists()) tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error: ${e.localizedMessage}")
        }
    }
}