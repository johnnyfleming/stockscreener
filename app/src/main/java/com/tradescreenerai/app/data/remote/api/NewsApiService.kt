package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {

    @GET("everything")
    suspend fun getEverything(
        @Query("q") query: String,
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("pageSize") pageSize: Int = 30,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en",
        @Query("apiKey") apiKey: String
    ): JsonObject

    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String = "business",
        @Query("country") country: String = "us",
        @Query("pageSize") pageSize: Int = 20,
        @Query("apiKey") apiKey: String
    ): JsonObject
}

