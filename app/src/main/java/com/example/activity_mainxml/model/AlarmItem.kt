data class AlarmItem(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val hour: Int,
    val minute: Int,
    val message: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val voiceName: String = "",
    val localFilePath: String? = null // 💡 이 줄을 추가하세요!
)