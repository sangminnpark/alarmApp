package com.example.activity_mainxml.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.activity_mainxml.AlarmAlertActivity
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val days = intent.getIntegerArrayListExtra("repeatDays") ?: arrayListOf()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        if (!days.contains(today)) return

        val msg = intent.getStringExtra("msg") ?: ""
        val voiceName = intent.getStringExtra("voiceName") // voiceName 추가 추출

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", msg)
            putExtra("voiceName", voiceName) // 데이터 전달
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alertIntent)
    }
}