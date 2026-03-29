package com.tradescreenerai.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tradescreenerai.app.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "myscreener_prefs")

class LocalDataRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val WATCHLIST_KEY              = stringPreferencesKey("watchlist")
        private val PORTFOLIO_KEY              = stringPreferencesKey("portfolio")
        private val ALERTS_KEY                 = stringPreferencesKey("alerts")
        private val INDICATOR_ALERTS_KEY       = stringPreferencesKey("indicator_alerts")
        private val THEME_KEY                  = stringPreferencesKey("theme_mode")
        private val BUY_SIGNAL_CONFIG_KEY      = stringPreferencesKey("buy_signal_config")
        private val BUY_SIGNAL_LAST_IDS_KEY    = stringPreferencesKey("buy_signal_last_ids")
        private val DAY_TRADER_SETTINGS_KEY    = stringPreferencesKey("day_trader_settings")
        private val NOTIF_PREFS_KEY            = stringPreferencesKey("notification_prefs")
        private val LAST_NEWS_NOTIF_KEY        = stringPreferencesKey("last_news_notif_id")
        private val SENT_MARKET_EVENTS_KEY     = stringPreferencesKey("sent_market_events")
    }

    // ===== WATCHLIST =====
    val watchlist: Flow<List<WatchlistItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[WATCHLIST_KEY] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<WatchlistItem>>() {}.type)
    }

    suspend fun addToWatchlist(item: WatchlistItem) {
        context.dataStore.edit { prefs ->
            val current: MutableList<WatchlistItem> = getCurrentWatchlist(prefs).toMutableList()
            if (current.none { it.symbol == item.symbol && it.type == item.type }) {
                current.add(item)
            }
            prefs[WATCHLIST_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeFromWatchlist(symbol: String, type: AssetType) {
        context.dataStore.edit { prefs ->
            val current = getCurrentWatchlist(prefs).toMutableList()
            current.removeAll { it.symbol == symbol && it.type == type }
            prefs[WATCHLIST_KEY] = gson.toJson(current)
        }
    }

    suspend fun isInWatchlist(symbol: String, type: AssetType): Boolean {
        var result = false
        context.dataStore.data.collect { prefs ->
            result = getCurrentWatchlist(prefs).any { it.symbol == symbol && it.type == type }
            return@collect
        }
        return result
    }

    private fun getCurrentWatchlist(prefs: Preferences): List<WatchlistItem> {
        val json = prefs[WATCHLIST_KEY] ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<WatchlistItem>>() {}.type)
    }

    // ===== PORTFOLIO =====
    val portfolio: Flow<List<PortfolioItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[PORTFOLIO_KEY] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<PortfolioItem>>() {}.type)
    }

    suspend fun addToPortfolio(item: PortfolioItem) {
        context.dataStore.edit { prefs ->
            val current: MutableList<PortfolioItem> = getCurrentPortfolio(prefs).toMutableList()
            current.add(item)
            prefs[PORTFOLIO_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeFromPortfolio(symbol: String) {
        context.dataStore.edit { prefs ->
            val current = getCurrentPortfolio(prefs).toMutableList()
            current.removeAll { it.symbol == symbol }
            prefs[PORTFOLIO_KEY] = gson.toJson(current)
        }
    }

    suspend fun updatePortfolioPrice(symbol: String, newPrice: Double) {
        context.dataStore.edit { prefs ->
            val current = getCurrentPortfolio(prefs).toMutableList()
            val index = current.indexOfFirst { it.symbol == symbol }
            if (index >= 0) {
                current[index] = current[index].copy(currentPrice = newPrice)
            }
            prefs[PORTFOLIO_KEY] = gson.toJson(current)
        }
    }

    private fun getCurrentPortfolio(prefs: Preferences): List<PortfolioItem> {
        val json = prefs[PORTFOLIO_KEY] ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<PortfolioItem>>() {}.type)
    }

    // ===== PRICE ALERTS =====
    val alerts: Flow<List<PriceAlert>> = context.dataStore.data.map { prefs ->
        val json = prefs[ALERTS_KEY] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<PriceAlert>>() {}.type)
    }

    suspend fun addAlert(alert: PriceAlert) {
        context.dataStore.edit { prefs ->
            val current: MutableList<PriceAlert> = getCurrentAlerts(prefs).toMutableList()
            current.add(alert)
            prefs[ALERTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeAlert(id: String) {
        context.dataStore.edit { prefs ->
            val current = getCurrentAlerts(prefs).toMutableList()
            current.removeAll { it.id == id }
            prefs[ALERTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun toggleAlert(id: String) {
        context.dataStore.edit { prefs ->
            val current = getCurrentAlerts(prefs).toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(isActive = !current[index].isActive)
            }
            prefs[ALERTS_KEY] = gson.toJson(current)
        }
    }

    private fun getCurrentAlerts(prefs: Preferences): List<PriceAlert> {
        val json = prefs[ALERTS_KEY] ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<PriceAlert>>() {}.type)
    }

    // ===== INDICATOR ALERTS =====
    val indicatorAlerts: Flow<List<IndicatorAlert>> = context.dataStore.data.map { prefs ->
        val json = prefs[INDICATOR_ALERTS_KEY] ?: "[]"
        try { gson.fromJson(json, object : TypeToken<List<IndicatorAlert>>() {}.type) }
        catch (_: Exception) { emptyList() }
    }

    suspend fun addIndicatorAlert(alert: IndicatorAlert) {
        context.dataStore.edit { prefs ->
            val current: MutableList<IndicatorAlert> = getIndicatorAlerts(prefs).toMutableList()
            current.add(alert)
            prefs[INDICATOR_ALERTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeIndicatorAlert(id: String) {
        context.dataStore.edit { prefs ->
            val current = getIndicatorAlerts(prefs).toMutableList()
            current.removeAll { it.id == id }
            prefs[INDICATOR_ALERTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun toggleIndicatorAlert(id: String) {
        context.dataStore.edit { prefs ->
            val current = getIndicatorAlerts(prefs).toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) current[idx] = current[idx].copy(isActive = !current[idx].isActive)
            prefs[INDICATOR_ALERTS_KEY] = gson.toJson(current)
        }
    }

    private fun getIndicatorAlerts(prefs: Preferences): List<IndicatorAlert> {
        val json = prefs[INDICATOR_ALERTS_KEY] ?: "[]"
        return try { gson.fromJson(json, object : TypeToken<List<IndicatorAlert>>() {}.type) }
        catch (_: Exception) { emptyList() }
    }

    // ===== THEME =====
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = mode
        }
    }

    // ===== BUY SIGNAL CONFIG =====
    val buySignalConfig: Flow<com.tradescreenerai.app.data.model.BuySignalConfig> =
        context.dataStore.data.map { prefs ->
            val json = prefs[BUY_SIGNAL_CONFIG_KEY]
            if (json != null) gson.fromJson(json, com.tradescreenerai.app.data.model.BuySignalConfig::class.java)
            else com.tradescreenerai.app.data.model.BuySignalConfig()
        }

    suspend fun saveBuySignalConfig(config: com.tradescreenerai.app.data.model.BuySignalConfig) {
        context.dataStore.edit { prefs ->
            prefs[BUY_SIGNAL_CONFIG_KEY] = gson.toJson(config)
        }
    }

    suspend fun getLastBuySignalIds(): Set<String> {
        var ids = emptySet<String>()
        context.dataStore.data.collect { prefs ->
            val json = prefs[BUY_SIGNAL_LAST_IDS_KEY] ?: "[]"
            val list: List<String> = gson.fromJson(json, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
            ids = list.toSet()
            return@collect
        }
        return ids
    }

    suspend fun saveLastBuySignalIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[BUY_SIGNAL_LAST_IDS_KEY] = gson.toJson(ids.toList())
        }
    }

    // ===== DAY TRADER SETTINGS =====
    val dayTraderSettings: Flow<com.tradescreenerai.app.data.model.DayTraderSettings> =
        context.dataStore.data.map { prefs ->
            val json = prefs[DAY_TRADER_SETTINGS_KEY]
            if (json != null) {
                try { gson.fromJson(json, com.tradescreenerai.app.data.model.DayTraderSettings::class.java) }
                catch (_: Exception) { com.tradescreenerai.app.data.model.DayTraderSettings() }
            } else com.tradescreenerai.app.data.model.DayTraderSettings()
        }

    suspend fun saveDayTraderSettings(settings: com.tradescreenerai.app.data.model.DayTraderSettings) {
        context.dataStore.edit { prefs ->
            prefs[DAY_TRADER_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ===== NOTIFICATION PREFERENCES =====
    val notificationPrefs: Flow<com.tradescreenerai.app.data.model.NotificationPrefs> =
        context.dataStore.data.map { prefs ->
            val json = prefs[NOTIF_PREFS_KEY]
            if (json != null) {
                try { gson.fromJson(json, com.tradescreenerai.app.data.model.NotificationPrefs::class.java) }
                catch (_: Exception) { com.tradescreenerai.app.data.model.NotificationPrefs() }
            } else com.tradescreenerai.app.data.model.NotificationPrefs()
        }

    suspend fun saveNotificationPrefs(prefs: com.tradescreenerai.app.data.model.NotificationPrefs) {
        context.dataStore.edit { p -> p[NOTIF_PREFS_KEY] = gson.toJson(prefs) }
    }

    // ===== LAST NEWS NOTIFICATION ID (avoid duplicate news notifs) =====
    suspend fun getLastNewsNotifId(): String {
        var id = ""
        context.dataStore.data.collect { prefs ->
            id = prefs[LAST_NEWS_NOTIF_KEY] ?: ""
            return@collect
        }
        return id
    }

    suspend fun saveLastNewsNotifId(id: String) {
        context.dataStore.edit { prefs -> prefs[LAST_NEWS_NOTIF_KEY] = id }
    }

    // ===== SENT MARKET EVENTS (avoid duplicate market notifs per day) =====
    suspend fun getSentMarketEvents(): Set<String> {
        var events = emptySet<String>()
        context.dataStore.data.collect { prefs ->
            val json = prefs[SENT_MARKET_EVENTS_KEY] ?: "[]"
            val list: List<String> = try {
                gson.fromJson(json, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
            } catch (_: Exception) { emptyList() }
            events = list.toSet()
            return@collect
        }
        return events
    }

    suspend fun addSentMarketEvent(eventKey: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[SENT_MARKET_EVENTS_KEY] ?: "[]"
            val list: MutableList<String> = try {
                gson.fromJson<List<String>>(json, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type).toMutableList()
            } catch (_: Exception) { mutableListOf() }
            list.add(eventKey)
            // Keep only last 30 days of events
            val trimmed = if (list.size > 30) list.takeLast(30) else list
            prefs[SENT_MARKET_EVENTS_KEY] = gson.toJson(trimmed)
        }
    }
}

