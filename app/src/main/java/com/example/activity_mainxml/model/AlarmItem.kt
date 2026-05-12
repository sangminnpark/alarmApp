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
    val localFilePath: String? = null
)
