package com.example.activity_mainxml

import AlarmItem
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.activity_mainxml.network.RetrofitClient
import com.example.activity_mainxml.network.VoiceRepository
import com.example.activity_mainxml.receiver.AlarmReceiver
import java.io.File
import java.util.Calendar

object AlarmScheduler {

    suspend fun requestElevenLabsVoice(
        context: Context,
        recordFile: File,
        voiceName: String // 사용자가 설정한 목소리 이름
    ): Pair<String, File>? {
        return try {
            // 1. 일레븐랩스에 목소리 등록하고 voiceId 받기
            val voiceId = VoiceRepository.addElevenLabsVoice(recordFile, voiceName)

            if (voiceId != null) {
                // 2. 등록된 voiceId로 미리보기 파일 생성
                val previewText = "이 목소리를 선택합니다."
                val responseFile = VoiceRepository.makeElevenLabsVoiceFile(
                    voiceId = voiceId,
                    targetText = previewText,
                    context = context
                )

                if (responseFile != null) {
                    Pair(voiceId, responseFile) // 성공 시 ID와 파일 객체 반환
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "일레븐랩스 처리 중 예외: ${e.localizedMessage}")
            null
        }
    }

    fun schedule(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("msg", alarm.message)
            // 💡 [중요] 일레븐랩스를 쓸 경우 alarm.voiceName에 voiceId가 들어있어야 합니다.
            putExtra("voiceId", alarm.voiceName)
            putExtra("alarmId", alarm.id)
            putExtra("localFilePath", alarm.localFilePath)
            putIntegerArrayListExtra("repeatDays", ArrayList(alarm.repeatDays.toList()))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d("AlarmScheduler", "알람 등록 완료 ID: ${alarm.id}")
    }

    fun cancel(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}