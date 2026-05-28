package com.example.activity_mainxml.model

data class AlarmItem(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val hour: Int,
    val minute: Int,
    val message: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val voiceId: String = "",
    val voiceName: String = "",
    val localFilePath: String? = null,
    val userId: String = "guest",
    val isSoundEnabled: Boolean = true,    // 사운드 활성화 여부
    val isVibrationEnabled: Boolean = true, // 진동 활성화 여부
    val fadeInDurationSeconds: Int = 0      // 볼륨 점진적 증가 시간 (0: 사용 안함)
)
