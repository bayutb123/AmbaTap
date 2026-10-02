package com.ambacoding.ambatap.data.transfer

import android.content.Context
import android.net.Uri
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.repository.MacroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Ekspor/impor macro ke file yang dipilih lewat Storage Access Framework. */
@Singleton
class MacroFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MacroRepository,
) {
    /** @return jumlah macro yang ditulis. */
    suspend fun exportAll(uri: Uri): Int = export(uri, repository.observeMacros().first())

    suspend fun exportOne(uri: Uri, id: Long): Int {
        val macro = repository.getMacro(id) ?: return 0
        return export(uri, listOf(macro))
    }

    private suspend fun export(uri: Uri, macros: List<Macro>): Int = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Tidak bisa menulis file")
        stream.bufferedWriter().use { it.write(MacroTransfer.encode(macros)) }
        macros.size
    }

    /** @return jumlah macro yang ditambahkan. */
    suspend fun import(uri: Uri): Int {
        val content = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Tidak bisa membaca file")
            stream.bufferedReader().use { it.readText() }
        }
        val macros = MacroTransfer.decode(content, System.currentTimeMillis())
        macros.forEach { repository.save(it) }
        return macros.size
    }
}
