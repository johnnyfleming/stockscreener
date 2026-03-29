package com.tradescreenerai.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tradescreenerai.app.data.model.AppSettings
import com.tradescreenerai.app.data.repository.SettingsRepository
import com.tradescreenerai.app.ui.navigation.AppNavGraph
import com.tradescreenerai.app.ui.navigation.BottomNavBar
import com.tradescreenerai.app.ui.theme.MyScreenerTheme
import com.tradescreenerai.app.util.NotificationHelper
import com.tradescreenerai.app.workers.MarketNotificationScheduler
import com.tradescreenerai.app.workers.PriceAlertWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Create notification channels (idempotent)
        NotificationHelper.createChannels(this)

        // 2. Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3. Enqueue periodic price-alert + news worker (every 15 min, needs network)
        val priceAlertWork = PeriodicWorkRequestBuilder<PriceAlertWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "price_alert_check",
            ExistingPeriodicWorkPolicy.KEEP,
            priceAlertWork
        )

        // 4. Schedule market open/close notifications for today (and future days)
        MarketNotificationScheduler.scheduleAll(this)

        setContent {
            MyScreenerApp()
        }
    }
}

@Composable
fun MyScreenerApp() {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val darkTheme = when (settings.themeMode) {
        "light"  -> false
        "dark"   -> true
        else     -> isSystemInDarkTheme()
    }

    MyScreenerTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AppNavGraph(navController = navController)
            }
        }
    }
}