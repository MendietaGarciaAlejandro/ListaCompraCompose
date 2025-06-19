package com.example.listacompracompose

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object ProductListConverter {
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(List::class.java, Product::class.java)
    private val adapter = moshi.adapter<List<Product>>(type)

    @TypeConverter
    fun fromList(products: List<Product>): String = adapter.toJson(products)

    @TypeConverter
    fun toList(json: String): List<Product> = adapter.fromJson(json) ?: emptyList()
}