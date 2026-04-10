package com.example.activity_mainxml.data

import android.content.Context
import com.example.activity_mainxml.model.AlarmItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AlarmRepository {
    const val PREFS_NAME = "alarm_prefs"
    const val ALARM_KEY = "alarm_list"

    // 데이터를 저장할 때
    fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        // MainActivity와 달리 일반 object이므로 context. 을 꼭 붙여야 함
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ALARM_KEY, Gson().toJson(alarms)).apply()
    }

    // 데이터를 불러올 때
    fun loadAlarms(context: Context): List<AlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(ALARM_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<AlarmItem>>() {}.type
        return Gson().fromJson(json, type)
    }
}