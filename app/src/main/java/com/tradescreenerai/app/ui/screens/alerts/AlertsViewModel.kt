package com.tradescreenerai.app.ui.screens.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.workers.MarketNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AlertsState(
    val alerts: List<PriceAlert> = emptyList(),
    val indicatorAlerts: List<IndicatorAlert> = emptyList(),
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    val showAddDialog: Boolean = false,
    val showAddIndicatorDialog: Boolean = false
)

class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalDataRepository(application)

    private val _state = MutableStateFlow(AlertsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            localRepo.alerts.collect { alerts ->
                _state.value = _state.value.copy(alerts = alerts)
            }
        }
        viewModelScope.launch {
            localRepo.indicatorAlerts.collect { ia ->
                _state.value = _state.value.copy(indicatorAlerts = ia)
            }
        }
        viewModelScope.launch {
            localRepo.notificationPrefs.collect { prefs ->
                _state.value = _state.value.copy(notificationPrefs = prefs)
            }
        }
    }

    // ── Price Alerts ──────────────────────────────────────────────────────────
    fun showAddDialog() { _state.value = _state.value.copy(showAddDialog = true) }
    fun hideAddDialog() { _state.value = _state.value.copy(showAddDialog = false) }

    fun addAlert(symbol: String, name: String, targetPrice: Double, isAbove: Boolean, type: AssetType) {
        viewModelScope.launch {
            localRepo.addAlert(
                PriceAlert(
                    id = UUID.randomUUID().toString(),
                    symbol = symbol,
                    name = name,
                    targetPrice = targetPrice,
                    isAbove = isAbove,
                    type = type
                )
            )
            hideAddDialog()
        }
    }

    fun removeAlert(id: String) {
        viewModelScope.launch { localRepo.removeAlert(id) }
    }

    fun toggleAlert(id: String) {
        viewModelScope.launch { localRepo.toggleAlert(id) }
    }

    // ── Indicator Alerts ──────────────────────────────────────────────────────
    fun showAddIndicatorDialog() { _state.value = _state.value.copy(showAddIndicatorDialog = true) }
    fun hideAddIndicatorDialog() { _state.value = _state.value.copy(showAddIndicatorDialog = false) }

    fun addIndicatorAlert(symbol: String, name: String, alertType: IndicatorAlertType, assetType: AssetType) {
        viewModelScope.launch {
            localRepo.addIndicatorAlert(
                IndicatorAlert(
                    id = UUID.randomUUID().toString(),
                    symbol = symbol,
                    name = name,
                    alertType = alertType,
                    assetType = assetType
                )
            )
            hideAddIndicatorDialog()
        }
    }

    fun removeIndicatorAlert(id: String) {
        viewModelScope.launch { localRepo.removeIndicatorAlert(id) }
    }

    fun toggleIndicatorAlert(id: String) {
        viewModelScope.launch { localRepo.toggleIndicatorAlert(id) }
    }

    // ── Notification Preferences ──────────────────────────────────────────────
    fun togglePriceAlertNotifs() {
        val updated = _state.value.notificationPrefs.copy(
            priceAlertsEnabled = !_state.value.notificationPrefs.priceAlertsEnabled
        )
        saveNotifPrefs(updated)
    }

    fun toggleMarketSessionNotifs() {
        val updated = _state.value.notificationPrefs.copy(
            marketSessionEnabled = !_state.value.notificationPrefs.marketSessionEnabled
        )
        saveNotifPrefs(updated)
        if (updated.marketSessionEnabled) {
            // Re-schedule market workers when the user re-enables
            MarketNotificationScheduler.scheduleAll(getApplication())
        }
    }

    fun toggleNewsNotifs() {
        val updated = _state.value.notificationPrefs.copy(
            urgentNewsEnabled = !_state.value.notificationPrefs.urgentNewsEnabled
        )
        saveNotifPrefs(updated)
    }

    private fun saveNotifPrefs(prefs: NotificationPrefs) {
        viewModelScope.launch {
            localRepo.saveNotificationPrefs(prefs)
        }
    }
}
