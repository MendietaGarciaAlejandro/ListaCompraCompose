// Data.kt
package com.example.listacompracompose

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class Product(
    @PrimaryKey val name: String,
    val price: Double,
    var quantity: Int,
    var isChecked: Boolean = false,
    var category: String = "General"
)

@Entity(tableName = "historial_productos")
data class ProductHistory(
    @PrimaryKey val name: String,
    val price: Double,
    val quantity: Int,
    val category: String = "General",
    val isFavorite: Boolean = false
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Entity(tableName = "purchase_log")
data class PurchaseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val quantity: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProductStats(
    val name: String,
    val purchaseCount: Int,
    val avgPrice: Double,
    val minPrice: Double,
    val maxPrice: Double,
    val lastPrice: Double,
    val prevPrice: Double?,
    val totalSpent: Double,
    val lastTimestamp: Long
) {
    val trend: Double get() = if (prevPrice != null) lastPrice - prevPrice else 0.0
}
