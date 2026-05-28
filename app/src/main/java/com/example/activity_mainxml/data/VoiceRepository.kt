package com.example.activity_mainxml.data

import android.content.Context
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.data.remote.RetrofitClient.elevenLabsApi
import com.example.activity_mainxml.model.ElevenLabsTtsRequest
import com.example.activity_mainxml.util.FileUtil.saveTempFile
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
                val nameBody = voiceName.toRequestBody("text/plain".toMediaTypeOrNull())
                val mimeType = if (recordFile.name.endsWith(".wav")) "audio/wav" else "audio/mpeg"
                val requestFile = recordFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val audioPart = MultipartBody.Part.createFormData("files", recordFile.name, requestFile)

                val response = elevenLabsApi.addVoice(
                    apiKey = ELEVENLABS_API_KEY,
                    name = nameBody,
                    files = listOf(audioPart)
                )

                response.voice_id
            } catch (e: retrofit2.HttpException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun makeElevenLabsVoiceFile(
        voiceId: String,
        targetText: String,
        context: Context
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val ttsRequest = ElevenLabsTtsRequest(text = targetText)
                val response = elevenLabsApi.textToSpeech(
                    apiKey = ELEVENLABS_API_KEY,
                    voiceId = voiceId,
                    request = ttsRequest
                )

                if (response.isSuccessful && response.body() != null) {
                    saveTempFile(response.body()!!, "eleven_${voiceId}", ".mp3", context)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun deleteElevenLabsVoice(voiceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = elevenLabsApi.deleteVoice(
                    apiKey = ELEVENLABS_API_KEY,
                    voiceId = voiceId
                )
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
}
