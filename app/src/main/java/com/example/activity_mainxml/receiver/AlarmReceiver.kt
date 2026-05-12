package com.example.activity_mainxml.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.activity_mainxml.AlarmAlertActivity
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

        // 요일 반복 체크 (설정된 요일이 아니면 종료)
        if (days.isNotEmpty() && !days.contains(today)) return

        // 1. 데이터를 변수에 정확히 담기
        val msg = intent.getStringExtra("msg") ?: ""
        // 💡 AlarmScheduler에서 "voiceName"이라는 키로 voiceId를 넣었으므로 그대로 꺼냅니다.
        val voiceId = intent.getStringExtra("voiceId")
        val alarmId = intent.getIntExtra("alarmId", -1)
        val localFilePath = intent.getStringExtra("localFilePath")
        Log.d("AlarmReceiver", "알람 발생! ID: $alarmId, 메시지: $msg, 보이스ID: $voiceId")

        // 2. Intent 생성 및 데이터 전달
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", msg)
            putExtra("voiceId", voiceId)
            putExtra("localFilePath", localFilePath)
            putExtra("alarmId", alarmId)
            // 💡 JSON 데이터 전달 추가
            putExtra("alarm_json", intent.getStringExtra("alarm_json"))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // 3. Activity 실행
        context.startActivity(alertIntent)
    }
}