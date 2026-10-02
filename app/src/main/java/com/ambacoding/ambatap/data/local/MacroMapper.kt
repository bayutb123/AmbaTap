package com.ambacoding.ambatap.data.local

import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.ScreenInfo
import kotlinx.serialization.json.Json

/** Versi format JSON aksi; naikkan saat bentuk [MacroAction] berubah tidak kompatibel. */
const val MACRO_SCHEMA_VERSION = 1

/** Konfigurasi JSON bersama untuk penyimpanan dan impor/ekspor macro. */
val MacroJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun MacroEntity.toDomain(): Macro = Macro(
    id = id,
    name = name,
    actions = MacroJson.decodeFromString<List<MacroAction>>(actionsJson),
    screen = MacroJson.decodeFromString<ScreenInfo>(screenJson),
    config = MacroJson.decodeFromString<PlaybackConfig>(configJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Macro.toEntity(): MacroEntity = MacroEntity(
    id = id,
    name = name,
    actionsJson = MacroJson.encodeToString(actions),
    screenJson = MacroJson.encodeToString(screen),
    configJson = MacroJson.encodeToString(config),
    schemaVersion = MACRO_SCHEMA_VERSION,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
