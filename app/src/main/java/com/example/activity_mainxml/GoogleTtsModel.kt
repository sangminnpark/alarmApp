package com.example.activity_mainxml

// API 요청 전체 구조
data class TtsRequest(
    val input: TtsInput,
    val voice: TtsVoice,
    val audioConfig: TtsAudioConfig
)

// 입력 텍스트
data class TtsInput(val text: String)

// 목소리 설정 (성별/이름)
data class TtsVoice(
    val languageCode: String = "ko-KR",
    val name: String = "ko-KR-Neural2-A" // A: 여성, C: 남성
)

// 오디오 설정
data class TtsAudioConfig(
    val audioEncoding: String = "MP3",
    val pitch: Double = 0.0,
    val speakingRate: Double = 1.0
)

// API 응답 구조 (Base64 인코딩된 음성 데이터)
data class TtsResponse(val audioContent: String)