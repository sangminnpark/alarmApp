package com.example.activity_mainxml.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.activity_mainxml.AlarmScheduler
import com.example.activity_mainxml.data.AlarmRepository.ALARM_KEY
import com.example.activity_mainxml.data.AlarmRepository.PREFS_NAME
import com.example.activity_mainxml.model.AlarmItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(ALARM_KEY, null) ?: return
            val type = object : TypeToken<List<AlarmItem>>() {}.type
            val alarms: List<AlarmItem> = Gson().fromJson(json, type)
            alarms.filter { it.isEnabled }.forEach { AlarmScheduler.schedule(context, it) }
        }
    }
}