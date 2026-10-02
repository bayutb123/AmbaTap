package com.bayutb123.ambatap.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.bayutb123.ambatap.domain.model.AppSettings
import com.bayutb123.ambatap.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            countdownSeconds = prefs[COUNTDOWN_SECONDS] ?: defaults.countdownSeconds,
            showTouchIndicator = prefs[SHOW_TOUCH_INDICATOR] ?: defaults.showTouchIndicator,
            panelIdleOpacity = prefs[PANEL_IDLE_OPACITY] ?: defaults.panelIdleOpacity,
        )
    }

    override suspend fun setCountdownSeconds(seconds: Int) {
        dataStore.edit { it[COUNTDOWN_SECONDS] = seconds.coerceIn(0, 10) }
    }

    override suspend fun setShowTouchIndicator(show: Boolean) {
        dataStore.edit { it[SHOW_TOUCH_INDICATOR] = show }
    }

    override suspend fun setPanelIdleOpacity(opacity: Float) {
        dataStore.edit { it[PANEL_IDLE_OPACITY] = opacity.coerceIn(0.2f, 1f) }
    }

    private companion object {
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val SHOW_TOUCH_INDICATOR = booleanPreferencesKey("show_touch_indicator")
        val PANEL_IDLE_OPACITY = floatPreferencesKey("panel_idle_opacity")
    }
}
