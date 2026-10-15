package com.ambacoding.ambatap.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MacroEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: kolom triggersJson (jadwal & pemicu buka aplikasi).
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    companion object {
        const val NAME = "ambatap.db"
    }
}
