// Database.kt
package com.example.listacompracompose

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Product::class, Template::class], version = 4, exportSchema = false)
@TypeConverters(ProductListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migración de la versión 1 a 2 (vacía)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No se necesitan cambios
            }
        }

        // Migración de la versión 2 a 3 (añade columna isChecked)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Añade la nueva columna
                database.execSQL("ALTER TABLE productos ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                  CREATE TABLE IF NOT EXISTS plantillas (
                    name TEXT NOT NULL PRIMARY KEY,
                    products TEXT NOT NULL
                  )
                """.trimIndent())
                        }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "compra.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false) // Permite reinicio en desarrollo
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
