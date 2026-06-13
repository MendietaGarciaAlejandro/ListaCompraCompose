// Database.kt
package com.example.listacompracompose

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Product::class, Template::class, ProductHistory::class, PurchaseLog::class],
    version = 8, exportSchema = false
)
@TypeConverters(ProductListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun templateDao(): TemplateDao
    abstract fun productHistoryDao(): ProductHistoryDao
    abstract fun purchaseLogDao(): PurchaseLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migración v1 -> v2 (sin cambios)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes
            }
        }

        // Migración v2 -> v3: añade columna isChecked
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE productos ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migración v3 -> v4: crea tabla plantillas
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS plantillas (name TEXT NOT NULL PRIMARY KEY, products TEXT NOT NULL)")
            }
        }

        // Migración v4 -> v5: añade columna category
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE productos ADD COLUMN category TEXT NOT NULL DEFAULT 'General';")
            }
        }

        // Migración v5 -> v6: crea tabla historial_productos
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS historial_productos (name TEXT NOT NULL PRIMARY KEY, price REAL NOT NULL, quantity INTEGER NOT NULL, category TEXT NOT NULL DEFAULT 'General')"
                )
            }
        }

        // Migración v6 -> v7: añade columna isFavorite al historial
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE historial_productos ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migración v7 -> v8: crea tabla purchase_log
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS purchase_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, price REAL NOT NULL, quantity INTEGER NOT NULL, timestamp INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "compra.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}