package com.example.activity_mainxml.model

import java.util.Random

data class AlarmItem(
    val id: Int = Random().nextInt(10000),
    var hour: Int,
    var minute: Int,
    var message: String,
    var isEnabled: Boolean = true,
    var repeatDays: Set<Int> = emptySet(),
    var voiceName: String? = null // 초기값은 null
)
