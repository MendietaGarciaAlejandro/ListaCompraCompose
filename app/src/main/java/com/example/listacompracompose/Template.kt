package com.example.listacompracompose

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "plantillas")
@TypeConverters(ProductListConverter::class)
data class Template(
    @PrimaryKey val name: String,
    val products: List<Product>
)