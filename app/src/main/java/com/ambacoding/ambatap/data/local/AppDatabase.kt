package com.ambacoding.ambatap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MacroEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    companion object {
        const val NAME = "ambatap.db"
    }
}
