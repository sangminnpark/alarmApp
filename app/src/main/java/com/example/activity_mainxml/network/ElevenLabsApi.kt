// ElevenLabsApi.kt
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ElevenLabsApi {
    @Multipart
    @POST("v1/voices/add")
    suspend fun addVoice(
        @Header("xi-api-key") apiKey: String,
        @Part("name") name: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): VoiceAddResponse

    @POST("v1/text-to-speech/{voice_id}")
    suspend fun textToSpeech(
        @Header("xi-api-key") apiKey: String,
        @Path("voice_id") voiceId: String,
        @Query("output_format") outputFormat: String = "mp3_44100_128",
        @Body request: ElevenLabsTtsRequest
    ): Response<ResponseBody>

    @DELETE("v1/voices/{voice_id}")
    suspend fun deleteVoice(
        @Header("xi-api-key") apiKey: String,
        @Path("voice_id") voiceId: String
    ): Response<Map<String, String>> // ResponseBody 대신 Map으로 받아보세요.
}

data class VoiceAddResponse(val voice_id: String)