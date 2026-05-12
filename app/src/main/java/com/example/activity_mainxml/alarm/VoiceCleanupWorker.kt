package com.example.activity_mainxml.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.activity_mainxml.data.AlarmRepository
import com.example.activity_mainxml.data.VoiceRepository
import com.example.activity_mainxml.model.CustomVoice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 3일 이상 사용하지 않은 보이스를 자동으로 삭제하는 워커
 */
class VoiceCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val voicePrefs = context.getSharedPreferences("custom_voices_prefs", Context.MODE_PRIVATE)
        
        // 1. 데이터 로드
        val allVoices = loadVoices(voicePrefs)
        val allAlarms = AlarmRepository.loadAlarms(context)
        
        // 2. 현재 활성화된 알람에서 사용 중인 voiceId 추출
        val activeVoiceIds = allAlarms.asSequence()
            .filter { it.isEnabled }
            .map { it.voiceName }
            .toSet()
        
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000L
        
        val remainingVoices = mutableListOf<CustomVoice>()
        var deletedCount = 0

        allVoices.forEach { voice ->
            val isUnused = !activeVoiceIds.contains(voice.voiceId)
            val isExpired = (currentTime - voice.lastUsedAt) > threeDaysInMillis
            
            if (isUnused && isExpired) {
                // 3. 삭제 조건 충족 시 삭제 수행
                Log.d("VoiceCleanup", "미사용 보이스 삭제: ${voice.name}")
                VoiceRepository.deleteElevenLabsVoice(voice.voiceId)
                File(voice.previewPath).delete()
                deletedCount++
                
                sendDeletionNotification(context, voice.name)
            } else {
                remainingVoices.add(voice)
            }
        }
        
        // 4. 결과 저장
        if (deletedCount > 0) {
            saveVoices(voicePrefs, remainingVoices)
        }
        
        return Result.success()
    }

    private fun loadVoices(prefs: android.content.SharedPreferences): List<CustomVoice> {
        val json = prefs.getString("voice_list_v2", null) ?: return emptyList()
        val type = object : TypeToken<List<CustomVoice>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun saveVoices(prefs: android.content.SharedPreferences, voices: List<CustomVoice>) {
        val json = Gson().toJson(voices)
        prefs.edit { putString("voice_list_v2", json) }
    }

    private fun sendDeletionNotification(context: Context, voiceName: String) {
        val channelId = "cleanup_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "보이스 관리", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("보이스 자동 정리")
            .setContentText("'$voiceName' 보이스가 3일간 사용되지 않아 삭제되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
