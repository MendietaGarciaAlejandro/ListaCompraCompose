package com.example.listacompracompose

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

object SupabaseConfig {
    val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    val jsonMediaType = "application/json".toMediaType()

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun restUrl(table: String) = "$baseUrl/rest/v1/$table"

    fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            System.currentTimeMillis()
        }
    }
}
