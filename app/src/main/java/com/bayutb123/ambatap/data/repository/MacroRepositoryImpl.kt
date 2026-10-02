package com.bayutb123.ambatap.data.repository

import com.bayutb123.ambatap.data.local.MacroDao
import com.bayutb123.ambatap.data.local.toDomain
import com.bayutb123.ambatap.data.local.toEntity
import com.bayutb123.ambatap.domain.model.Macro
import com.bayutb123.ambatap.domain.repository.MacroRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MacroRepositoryImpl @Inject constructor(
    private val dao: MacroDao,
) : MacroRepository {

    override fun observeMacros(): Flow<List<Macro>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMacro(id: Long): Macro? = dao.getById(id)?.toDomain()

    override suspend fun save(macro: Macro): Long {
        val rowId = dao.upsert(macro.toEntity())
        // @Upsert mengembalikan -1 bila baris yang ada diperbarui.
        return if (macro.id != 0L) macro.id else rowId
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
