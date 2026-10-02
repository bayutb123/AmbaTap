package com.bayutb123.ambatap.domain.repository

import com.bayutb123.ambatap.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setCountdownSeconds(seconds: Int)
    suspend fun setShowTouchIndicator(show: Boolean)
    suspend fun setPanelIdleOpacity(opacity: Float)
}
