package com.ambacoding.ambatap.data.repository

import com.ambacoding.ambatap.data.local.MacroDao
import com.ambacoding.ambatap.data.local.toDomain
import com.ambacoding.ambatap.data.local.toEntity
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.repository.MacroRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MacroRepositoryImpl @Inject constructor(
    private val dao: MacroDao,
) : MacroRepository {

    override fun observeMacros(): Flow<List<Macro>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMacro(id: Long): Macro? = dao.getById(id)?.toDomain()

    override fun observeMacro(id: Long): Flow<Macro?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(macro: Macro): Long {
        val rowId = dao.upsert(macro.toEntity())
        // @Upsert mengembalikan -1 bila baris yang ada diperbarui.
        return if (macro.id != 0L) macro.id else rowId
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
