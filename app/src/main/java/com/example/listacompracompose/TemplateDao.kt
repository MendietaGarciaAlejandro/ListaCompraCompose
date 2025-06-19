package com.example.listacompracompose

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM plantillas ORDER BY name COLLATE NOCASE ASC")
    fun getAllTemplates(): Flow<List<Template>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: Template)

    @Query("SELECT * FROM plantillas WHERE name = :name")
    suspend fun getTemplate(name: String): Template?

    @Query("DELETE FROM plantillas WHERE name = :name")
    suspend fun deleteTemplate(name: String)
}