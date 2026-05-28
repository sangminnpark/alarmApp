package com.example.activity_mainxml.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.activity_mainxml.data.VoiceRepository
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.alarm.AlarmReceiver
import java.io.File
import java.util.Calendar

object AlarmScheduler {

    suspend fun requestElevenLabsVoice(
        context: Context,
        recordFile: File,
        voiceName: String
    ): Pair<String, File>? {
        return try {
            val voiceId = VoiceRepository.addElevenLabsVoice(recordFile, voiceName)
            if (voiceId != null) {
                val previewText = "이 목소리를 선택합니다."
                val responseFile = VoiceRepository.makeElevenLabsVoiceFile(
                    voiceId = voiceId,
                    targetText = previewText,
                    context = context
                )
                if (responseFile != null) Pair(voiceId, responseFile) else null
            } else null
        } catch (e: Exception) {
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
            putExtra("voiceId", alarm.voiceName)
            putExtra("alarmId", alarm.id)
            putExtra("localFilePath", alarm.localFilePath)
            putIntegerArrayListExtra("repeatDays", ArrayList(alarm.repeatDays.toList()))
            
            val alarmJson = com.google.gson.Gson().toJson(alarm)
            putExtra("alarm_json", alarmJson)
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

            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }

            if (alarm.repeatDays.isNotEmpty()) {
                while (!alarm.repeatDays.contains(get(Calendar.DAY_OF_WEEK))) {
                    add(Calendar.DATE, 1)
                }
            }
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
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
