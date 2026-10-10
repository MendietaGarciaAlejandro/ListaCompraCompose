package com.example.listacompracompose

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Borrado hecho en local que todavía no ha llegado a Supabase (p.ej. sin cobertura).
 * Se guarda para reintentarlo y para que el pull no vuelva a traer el producto.
 */
@Entity(tableName = "pending_deletes", primaryKeys = ["kind", "name"])
data class PendingDelete(
    val kind: String,
    val name: String,
    // true = comprado y marcado: es lo que debe sumar al inventario de casa
    val bought: Boolean = false,
    // cantidad que tenía en el móvil al borrarlo (pudo cambiarse sin red)
    val quantity: Int? = null
) {
    companion object {
        const val KIND_PRODUCT = "product"
        const val KIND_TEMPLATE = "template"
    }
}

/**
 * Producto creado en local que todavía no ha llegado a Supabase. Mientras esté aquí,
 * que no aparezca en el servidor no significa que alguien lo haya borrado.
 */
@Entity(tableName = "pending_uploads")
data class PendingUpload(@PrimaryKey val name: String)

@Dao
interface PendingDeleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingDelete)

    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDelete>

    @Query("SELECT * FROM pending_deletes WHERE kind = :kind AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun get(kind: String, name: String): PendingDelete?

    @Query("DELETE FROM pending_deletes WHERE kind = :kind AND name = :name COLLATE NOCASE")
    suspend fun delete(kind: String, name: String)
}

@Dao
interface PendingUploadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingUpload)

    @Query("SELECT name FROM pending_uploads")
    suspend fun getAllNames(): List<String>

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE name = :name COLLATE NOCASE")
    suspend fun count(name: String): Int

    @Query("DELETE FROM pending_uploads WHERE name = :name COLLATE NOCASE")
    suspend fun delete(name: String)
}
