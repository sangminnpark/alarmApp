// ElevenLabs 전용 모델 (이름을 다르게 하면 안 헷갈립니다)
data class ElevenLabsTtsRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",
    val voice_settings: ElevenLabsVoiceSettings? = null
)

// 목소리 설정 (일단 null로 보내도 되지만 클래스는 정의되어 있어야 함)
data class ElevenLabsVoiceSettings(
    val stability: Float = 0.5f,
    val similarity_boost: Float = 0.75f,
    val style: Float = 0.0f,
    val use_speaker_boost: Boolean = true
)