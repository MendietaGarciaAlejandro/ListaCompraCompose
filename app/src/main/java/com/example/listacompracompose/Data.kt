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
    val category: String = "General"
)
