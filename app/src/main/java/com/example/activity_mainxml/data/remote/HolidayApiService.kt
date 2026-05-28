package com.example.activity_mainxml.data.remote

import com.example.activity_mainxml.model.HolidayResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface HolidayApiService {
    @GET("getRestDeInfo") // 💡 getHoliDeInfo 대신 모든 공휴일/대체공휴일을 포함하는 getRestDeInfo 사용
    suspend fun getHolidays(
        @Query(value = "ServiceKey", encoded = true) apiKey: String,
        @Query("solYear") year: Int,
        @Query("solMonth") month: String,
        @Query("_type") format: String = "json",
        @Query("numOfRows") rows: Int = 100
    ): Response<HolidayResponse>
}
