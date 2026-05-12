package com.example.activity_mainxml.model

import com.google.gson.annotations.SerializedName

// --- Google Cloud TTS Models ---
data class TtsModel(
    val input: TtsInput,
    val voice: TtsVoice,
    val audioConfig: TtsAudioConfig
)

data class TtsInput(val text: String)
data class TtsVoice(val languageCode: String, val name: String)
data class TtsAudioConfig(val audioEncoding: String = "MP3")

data class TtsResponse(
    @SerializedName("audioContent")
    val audioContent: String
)

// --- ElevenLabs TTS Models ---
data class ElevenLabsTtsRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",
    val voice_settings: ElevenLabsVoiceSettings? = null
)

data class ElevenLabsVoiceSettings(
    val stability: Float = 0.5f,
    val similarity_boost: Float = 0.75f,
    val style: Float = 0.0f,
    val use_speaker_boost: Boolean = true
)
