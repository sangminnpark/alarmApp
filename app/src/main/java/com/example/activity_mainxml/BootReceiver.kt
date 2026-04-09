package com.example.activity_mainxml

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 부팅 완료 신호(일반 또는 보안 부팅) 확인
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(ALARM_KEY, null)

            if (!json.isNullOrBlank()) {
                val alarms: List<AlarmItem> = Gson().fromJson(json, object : TypeToken<List<AlarmItem>>() {}.type)

                // 저장되어 있던 알람 중 활성화된 것들만 재등록
                alarms.filter { it.isEnabled }.forEach { alarm ->
                    AlarmScheduler.schedule(context, alarm)
                }
            }
        }
    }
}