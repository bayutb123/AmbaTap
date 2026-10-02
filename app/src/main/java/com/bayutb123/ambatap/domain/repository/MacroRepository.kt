package com.bayutb123.ambatap.domain.repository

import com.bayutb123.ambatap.domain.model.Macro
import kotlinx.coroutines.flow.Flow

interface MacroRepository {
    fun observeMacros(): Flow<List<Macro>>
    suspend fun getMacro(id: Long): Macro?

    /** Menyimpan macro baru (id = 0) atau memperbarui yang ada. Mengembalikan id. */
    suspend fun save(macro: Macro): Long
    suspend fun delete(id: Long)
}
