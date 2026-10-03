package com.ambacoding.ambatap.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: Long): MacroEntity?

    @Query("SELECT * FROM macros WHERE id = :id")
    fun observeById(id: Long): Flow<MacroEntity?>

    @Upsert
    suspend fun upsert(entity: MacroEntity): Long

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteById(id: Long)
}
