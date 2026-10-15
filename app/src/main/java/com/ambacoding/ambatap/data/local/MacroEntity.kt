package com.ambacoding.ambatap.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aksi, info layar, dan konfigurasi disimpan sebagai JSON agar format aksi bisa
 * berkembang tanpa migrasi skema tabel; [schemaVersion] menandai versi format JSON.
 */
@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val actionsJson: String,
    val screenJson: String,
    val configJson: String,
    @ColumnInfo(defaultValue = "{}") val triggersJson: String = "{}",
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
