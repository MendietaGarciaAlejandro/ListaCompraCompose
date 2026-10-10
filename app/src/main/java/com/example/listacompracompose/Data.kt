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
    var category: String = "General",
    var updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "historial_productos")
data class ProductHistory(
    @PrimaryKey val name: String,
    val price: Double,
    val quantity: Int,
    val category: String = "General",
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Entity(tableName = "purchase_log")
data class PurchaseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val quantity: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val remoteId: String? = null
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

data class MonthlyStats(
    val year: Int,
    val month: Int,       // 1-12
    val totalSpent: Double,
    val tripCount: Int,   // distinct shopping days
    val itemCount: Int    // total units purchased
)

@androidx.room.Entity(tableName = "shopping_sessions")
data class ShoppingSession(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val itemCount: Int = 0,
    val totalSpent: Double = 0.0,
    val remoteId: String? = null
)

data class GlobalStats(
    val totalSpent: Double,
    val totalTrips: Int,
    val avgSpentPerMonth: Double,
    val avgSpentPerTrip: Double,
    val mostBoughtProduct: String?,
    val monthlyBreakdown: List<MonthlyStats>  // últimos 6 meses, cronológico
)
