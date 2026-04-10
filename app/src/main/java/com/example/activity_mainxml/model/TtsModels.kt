package com.example.activity_mainxml.model

data class TtsModel(
    val input: TtsInput,
    val voice: TtsVoice,
    val audioConfig: TtsAudioConfig
)

data class TtsInput(val text: String)
data class TtsVoice(val languageCode: String, val name: String)
data class TtsAudioConfig(val audioEncoding: String = "MP3")

// 응답 모델 (Base64 인코딩된 오디오 데이터가 옴)
data class TtsResponse(val audioContent: String)
