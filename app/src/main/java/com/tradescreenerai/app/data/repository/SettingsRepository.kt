package com.tradescreenerai.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tradescreenerai.app.data.model.AppSettings
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Separate DataStore from LocalDataRepository ("myscreener_prefs") to avoid conflicts
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings_prefs")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("app_settings")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[SETTINGS_KEY]
        if (json != null) {
            try { gson.fromJson(json, AppSettings::class.java) }
            catch (_: Exception) { AppSettings() }
        } else AppSettings()
    }

    suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    /** Read current settings, apply [transform], then persist. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var current = AppSettings()
        context.settingsDataStore.data.collect { prefs ->
            val json = prefs[SETTINGS_KEY]
            current = if (json != null) {
                try { gson.fromJson(json, AppSettings::class.java) }
                catch (_: Exception) { AppSettings() }
            } else AppSettings()
            return@collect
        }
        save(transform(current))
    }
}

