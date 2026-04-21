package com.example.activity_mainxml.network

import ElevenLabsTtsRequest
import android.content.Context
import android.util.Log
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.network.FileUtil.saveTempFile
import com.example.activity_mainxml.network.RetrofitClient.elevenLabsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

object VoiceRepository {
    private const val ELEVENLABS_API_KEY = BuildConfig.ELEVEN_LABS_API_KEY

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

    suspend fun deleteElevenLabsVoice(voiceId: String): Boolean {
        Log.d("RetrofitClient", "서버 삭제 시도 중... ID: [$voiceId]")
        return withContext(Dispatchers.IO) {
            try {
                // 위에서 이름을 통일한 elevenLabsService를 사용합니다.
                val response = elevenLabsApi.deleteVoice(
                    apiKey = ELEVENLABS_API_KEY,
                    voiceId = voiceId
                )

                if (response.isSuccessful) {
                    Log.d("RetrofitClient", "서버 삭제 성공: $voiceId")
                    true
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("RetrofitClient", "서버 삭제 실패: ${response.code()} - $errorBody")
                    false
                }
            } catch (e: Exception) {
                Log.e("RetrofitClient", "삭제 중 예외 발생: ${e.localizedMessage}")
                false
            }
        }
    }
}