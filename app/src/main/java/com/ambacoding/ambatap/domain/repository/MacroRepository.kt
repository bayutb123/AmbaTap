package com.ambacoding.ambatap.domain.repository

import com.ambacoding.ambatap.domain.model.Macro
import kotlinx.coroutines.flow.Flow

interface MacroRepository {
    fun observeMacros(): Flow<List<Macro>>
    suspend fun getMacro(id: Long): Macro?
    fun observeMacro(id: Long): Flow<Macro?>

    /** Menyimpan macro baru (id = 0) atau memperbarui yang ada. Mengembalikan id. */
    suspend fun save(macro: Macro): Long
    suspend fun delete(id: Long)
}
