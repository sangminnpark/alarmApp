data class CustomVoice(
    val id: String,        // 파일 식별자
    val displayName: String, // 사용자에게 보여줄 이름 (예: "내 목소리 1")
    val filePath: String     // 서버에서 받아와 저장된 .wav 파일 경로
)