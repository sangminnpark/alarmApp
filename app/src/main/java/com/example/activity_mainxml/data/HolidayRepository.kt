package com.example.activity_mainxml.data

import android.content.Context
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.data.remote.RetrofitClient
import com.example.activity_mainxml.model.HolidayItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

object HolidayRepository {
    private const val PREFS_NAME = "holiday_prefs"
    private const val HOLIDAY_LIST_KEY = "saved_holidays"
    private val API_KEY = BuildConfig.HOLIDAY_API_KEY

    /**
     * 이번 달의 공휴일을 API에서 가져와 로컬에 저장합니다.
     */
    suspend fun fetchAndSaveHolidays(context: Context) {
        try {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)

            val response = RetrofitClient.holidayApi.getHolidays(API_KEY, year, month)
            if (response.isSuccessful) {
                val rawItems = response.body()?.response?.body?.items?.item
                val holidays = parseHolidays(rawItems)
                
                if (holidays.isNotEmpty()) {
                    saveHolidaysToLocal(context, holidays)
                }
            }
        } catch (e: Exception) { }
    }

    private fun parseHolidays(item: Any?): List<Int> {
        if (item == null) return emptyList()
        val gson = Gson()
        return try {
            val type = object : TypeToken<List<HolidayItem>>() {}.type
            val list = gson.fromJson<List<HolidayItem>>(gson.toJson(item), type)
            list.map { it.locdate }
        } catch (e: Exception) {
            // 단일 항목일 경우 처리
            try {
                val singleItem = gson.fromJson(gson.toJson(item), HolidayItem::class.java)
                listOf(singleItem.locdate)
            } catch (e2: Exception) { emptyList() }
        }
    }

    private fun saveHolidaysToLocal(context: Context, holidays: List<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 기존 데이터와 합쳐서 저장 (중복 제거)
        val existing = getSavedHolidays(context).toSet()
        val total = (existing + holidays).toSet()
        prefs.edit().putString(HOLIDAY_LIST_KEY, Gson().toJson(total.toList())).apply()
    }

    fun getSavedHolidays(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(HOLIDAY_LIST_KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) { emptyList() }
    }
}
