package com.example.activity_mainxml.network

import ElevenLabsApi
import ElevenLabsTtsRequest
import android.content.Context
import android.util.Log
import com.example.activity_mainxml.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
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

    /**
     * [ElevenLabs] 목소리 등록 (최초 1회 실행)
     * 녹음 파일을 보내 voice_id를 발급받습니다.
     */
    suspend fun addElevenLabsVoice(recordFile: File, voiceName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 이름 데이터 준비
                val nameBody = voiceName.toRequestBody("text/plain".toMediaTypeOrNull())

                // 2. 파일 데이터 준비 (MIME 타입을 구체적으로 지정하는 것이 안전합니다)
                // m4a인 경우 "audio/mp4", wav인 경우 "audio/wav"
                val mimeType = if (recordFile.name.endsWith(".wav")) "audio/wav" else "audio/mpeg"
                val requestFile = recordFile.asRequestBody(mimeType.toMediaTypeOrNull())

                // 💡 일레븐랩스 API는 필드 이름이 "files"여야 하며, 리스트 형태를 받습니다.
                val audioPart =
                    MultipartBody.Part.createFormData("files", recordFile.name, requestFile)

                // 3. API 호출
                val response = elevenLabsApi.addVoice(
                    apiKey = ELEVENLABS_API_KEY,
                    name = nameBody,
                    files = listOf(audioPart)
                )

                Log.d("RetrofitClient", "일레븐랩스 목소리 등록 성공: ${response.voice_id}")
                response.voice_id

            } catch (e: retrofit2.HttpException) {
                // ✅ HTTP 400 등 서버 응답 에러 발생 시 상세 이유 출력
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("RetrofitClient", "HTTP 에러 발생 ($String.valueOf(e.code())): $errorBody")
                null
            } catch (e: Exception) {
                Log.e("RetrofitClient", "일레븐랩스 등록 실패: ${e.localizedMessage}")
                null
            }
        }
    }

    /**
     * [ElevenLabs] 보이스 생성 (TTS)
     * 저장된 voiceId를 사용하여 텍스트를 음성 파일로 변환합니다.
     */
    suspend fun makeElevenLabsVoiceFile(
        voiceId: String,
        targetText: String,
        context: Context
    ): File? {
        Log.d("RETROFIT_DEBUG", "서버 요청 시작! ID: $voiceId, 텍스트: $targetText")
        return withContext(Dispatchers.IO) {
            try {
                // ✅ 수정한 전용 모델 클래스 사용
                val ttsRequest = ElevenLabsTtsRequest(
                    text = targetText,
                    model_id = "eleven_multilingual_v2"
                    // voice_settings는 기본값이 null이므로 생략 가능합니다.
                )

                val response = elevenLabsApi.textToSpeech(
                    apiKey = ELEVENLABS_API_KEY,
                    voiceId = voiceId,
                    outputFormat = "mp3_44100_128",
                    request = ttsRequest // 여기서 인터페이스에 전달
                )

                if (response.isSuccessful && response.body() != null) {
                    saveTempFile(response.body()!!, "eleven_${voiceId}", ".mp3", context)
                } else {
                    Log.e("RetrofitClient", "실패: ${response.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("RetrofitClient", "오류: ${e.localizedMessage}")
                null
            }
        }
    }

    /**
     * 공통: 응답 받은 ByteStream을 파일로 저장하는 유틸리티
     */
    private fun saveTempFile(
        body: ResponseBody,
        prefix: String,
        extension: String,
        context: Context
    ): File? {
        return try {
            val fileName = "${prefix}${System.currentTimeMillis()}${extension}"
            val outputFile = File(context.filesDir, fileName)
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (e: Exception) {
            null
        }
    }
}