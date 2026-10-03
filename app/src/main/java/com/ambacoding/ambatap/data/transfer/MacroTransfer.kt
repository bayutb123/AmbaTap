package com.ambacoding.ambatap.data.transfer

import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.ScreenInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Isi file ekspor `.json`. [format] dan [version] memastikan file berasal dari AmbaTap. */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val macros: List<ExportedMacro>,
) {
    companion object {
        const val FORMAT = "ambatap-macros"
        const val VERSION = 1
    }
}

@Serializable
data class ExportedMacro(
    val name: String,
    val screen: ScreenInfo,
    val config: PlaybackConfig = PlaybackConfig(),
    val actions: List<MacroAction>,
    val createdAt: Long = 0,
)

class InvalidMacroFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Konversi antara macro dan file ekspor; tanpa I/O agar mudah diuji. */
object MacroTransfer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(macros: List<Macro>): String = json.encodeToString(
        ExportFile(
            macros = macros.map { ExportedMacro(it.name, it.screen, it.config, it.actions, it.createdAt) },
        ),
    )

    /**
     * @return macro baru (id 0) siap disimpan.
     * @throws InvalidMacroFileException bila isi bukan file ekspor AmbaTap yang didukung.
     */
    fun decode(content: String, nowMs: Long): List<Macro> {
        val file = try {
            json.decodeFromString<ExportFile>(content)
        } catch (e: SerializationException) {
            throw InvalidMacroFileException("Bukan file macro AmbaTap", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidMacroFileException("Bukan file macro AmbaTap", e)
        }
        if (file.format != ExportFile.FORMAT) throw InvalidMacroFileException("Bukan file macro AmbaTap")
        if (file.version > ExportFile.VERSION) {
            throw InvalidMacroFileException("File dibuat versi AmbaTap yang lebih baru")
        }
        return file.macros.map {
            Macro(
                name = it.name,
                actions = it.actions,
                screen = it.screen,
                config = it.config,
                createdAt = it.createdAt.takeIf { created -> created > 0 } ?: nowMs,
                updatedAt = nowMs,
            )
        }
    }
}
