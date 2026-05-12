package com.example.activity_mainxml.model

/**
 * 커스텀 보이스 정보를 담는 데이터 클래스
 */
data class CustomVoice(
    val name: String,
    val voiceId: String,
    val previewPath: String,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis() // 마지막으로 알람에 설정된 시간
)
