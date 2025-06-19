// Dao.kt
package com.example.listacompracompose

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Dao.kt
@Dao
interface ProductDao {
    @Query("SELECT * FROM productos ORDER BY isChecked ASC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prod: Product)

    @Query("DELETE FROM productos WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String): Int

    @Update
    suspend fun update(prod: Product)

    @Query("SELECT * FROM productos WHERE LOWER(name) = LOWER(:name)")
    suspend fun getByName(name: String): Product?

    @Query("UPDATE productos SET isChecked = :isChecked WHERE name = :name")
    suspend fun updateCheckedStatus(name: String, isChecked: Boolean)

//    @Query("DELETE FROM productos")
//    suspend fun deleteAllProducts()
}
