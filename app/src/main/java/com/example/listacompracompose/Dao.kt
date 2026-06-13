// Dao.kt
package com.example.listacompracompose

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM productos WHERE isChecked = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getPendingSync(): List<Product>
}

@Dao
interface ProductHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ProductHistory)

    @Query("SELECT * FROM historial_productos WHERE LOWER(name) LIKE LOWER(:query) || '%' ORDER BY isFavorite DESC, name COLLATE NOCASE ASC LIMIT 6")
    suspend fun search(query: String): List<ProductHistory>

    @Query("SELECT * FROM historial_productos WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFavorites(): List<ProductHistory>

    @Query("UPDATE historial_productos SET isFavorite = :isFavorite WHERE name = :name")
    suspend fun setFavorite(name: String, isFavorite: Boolean)

    @Query("SELECT * FROM historial_productos WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): ProductHistory?
}

@Dao
interface PurchaseLogDao {
    @Insert
    suspend fun insert(log: PurchaseLog)

    @Query("SELECT * FROM purchase_log ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<PurchaseLog>
}

@Dao
interface ShoppingSessionDao {
    @Insert
    suspend fun insert(session: ShoppingSession)

    @Query("SELECT * FROM shopping_sessions ORDER BY timestamp DESC")
    suspend fun getAll(): List<ShoppingSession>

    @Query("SELECT COUNT(*) FROM shopping_sessions WHERE timestamp >= :from AND timestamp < :to")
    suspend fun countInRange(from: Long, to: Long): Int
}
