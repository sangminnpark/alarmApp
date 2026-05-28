package com.example.activity_mainxml.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.ui.alert.AlarmAlertActivity
import com.example.activity_mainxml.util.HolidayUtil
import com.google.gson.Gson
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val days = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getIntegerArrayListExtra("repeatDays") ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getIntegerArrayListExtra("repeatDays") ?: arrayListOf()
        }
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        if (days.isNotEmpty() && !days.contains(today)) return

        val msg = intent.getStringExtra("msg") ?: ""
        val voiceId = intent.getStringExtra("voiceId")
        val alarmId = intent.getIntExtra("alarmId", -1)
        val localFilePath = intent.getStringExtra("localFilePath")
        val alarmJson = intent.getStringExtra("alarm_json")

        // 💡 [공휴일 제외 로직]
        if (alarmJson != null) {
            val alarm = Gson().fromJson(alarmJson, AlarmItem::class.java)
            if (alarm.isExcludeHolidays && HolidayUtil.isHoliday(context, Calendar.getInstance())) {
                return
            }
        }

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", msg)
            putExtra("voiceId", voiceId)
            putExtra("localFilePath", localFilePath)
            putExtra("alarmId", alarmId)
            putExtra("alarm_json", alarmJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        context.startActivity(alertIntent)
    }
}
