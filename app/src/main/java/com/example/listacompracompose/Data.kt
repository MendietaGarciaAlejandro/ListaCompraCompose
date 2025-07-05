// Data.kt
package com.example.listacompracompose

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class Product(
    @PrimaryKey val name: String,
    val price: Double,
    var quantity: Int,
    var isChecked: Boolean = false, // Nuevo campo para estado "tachado"
    var category: String = "General" // Nueva propiedad para la categoría por defecto
)
