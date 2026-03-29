# AGENTS.md — MyScreener Android App

## Project Overview
Android stock/crypto/commodity screener app built with Kotlin + Jetpack Compose. MVVM architecture with a manual (no DI framework) dependency wiring pattern.

## Build & Run
```powershell
# Debug build
.\gradlew assembleDebug

# Run unit tests
.\gradlew testDebugUnitTest

# Install on connected device
.\gradlew installDebug
```
- **minSdk 30**, **targetSdk/compileSdk 36**, AGP 9.1.0, Kotlin 2.2.10
- API keys are embedded in `app/build.gradle.kts` as `buildConfigField` entries (AlphaVantage, Finnhub, NewsAPI, Polygon). Access them via `BuildConfig.ALPHA_VANTAGE_KEY` etc.

## Architecture Layers
```
app/src/main/java/com/tradescreenerai/app/
  data/
    model/          – Shared data classes & enums (Models.kt, CommodityModels.kt, DayTraderModels.kt, StockRankingModels.kt)
    remote/api/     – Retrofit interface definitions (one file per external API)
    remote/RetrofitClient.kt  – Single object with lazy-initialized API instances
    repository/     – One repository per domain: Stock, Crypto, Commodity, MarketData, News, LocalData, Settings
  domain/           – Pure business logic objects (TechnicalAnalysis, ConfluenceScoreEngine, SmartSignalEngine, BacktestEngine, etc.)
  ui/
    navigation/     – Screen.kt (sealed routes), AppNavGraph.kt, BottomNavBar.kt
    screens/        – One sub-package per screen, each containing a Screen + ViewModel file
    components/     – Reusable Compose components (cards/, charts/, common/)
    theme/          – Color.kt, Theme.kt, Type.kt
  util/             – Constants.kt, Resource.kt, NotificationHelper.kt
  workers/          – WorkManager workers (PriceAlertWorker, MarketSessionWorker, MarketNotificationScheduler)
```

## Key Conventions

### Repository Pattern & No DI
Repositories are instantiated directly inside ViewModels — there is **no Hilt/Koin**. All API clients come from the `RetrofitClient` singleton object. When creating a new screen, instantiate repositories in the ViewModel constructor.

### State Management
Every ViewModel follows this pattern:
```kotlin
data class FooState(val items: List<X> = emptyList(), val isLoading: Boolean = true, val error: String? = null)
class FooViewModel : ViewModel() {
    private val _state = MutableStateFlow(FooState())
    val state = _state.asStateFlow()
}
```

### Resource Wrapper
All repository calls return `Resource<T>` (`util/Resource.kt`):
```kotlin
sealed class Resource<T> { class Success<T>(data: T); class Error<T>(message, data?); class Loading<T>(data?) }
```
Handle all three cases in ViewModels; never let `Error` silently drop.

### Navigation
Routes are defined in `Screen.kt` as a sealed class. Detail screens use typed route helpers:
```kotlin
Screen.StockDetail.createRoute(symbol)   // "stock_detail/$symbol"
Screen.DayTrader.createRoute(symbol, "STOCK")
```
Add new screens to both `Screen.kt` and `AppNavGraph.kt`.

## External API Strategy
Each data type has a **primary + fallback** chain to handle rate limits:

| Data | Primary | Fallback(s) |
|------|---------|-------------|
| Stock quotes/charts | Yahoo Finance (free, no key) | Finnhub → AlphaVantage |
| Crypto markets | CoinGecko | CoinPaprika (after 429 — 90 s backoff via `Constants.RATE_LIMIT_BACKOFF_MS`) |
| Commodity prices | Yahoo Finance batch (ETF proxies) | Finnhub individual + Polygon agg bars |
| News | NewsAPI | — |
| Fear & Greed | alternative.me | — |

Commodity data uses **ETF ticker proxies**: `GLD`→Gold, `SLV`→Silver, `USO`→WTI Oil, `BNO`→Brent, `UNG`→Nat Gas, `CPER`→Copper, etc. (see `CommodityRepository.kt` header).

Yahoo Finance requires a special `OkHttpClient` with browser-like headers — see `RetrofitClient.yahooHttpClient`. Do not reuse the default client for Yahoo endpoints.

## Local Persistence
DataStore Preferences only — **no Room/SQLite**. Two separate stores to avoid key collisions:
- `"myscreener_prefs"` — watchlist, portfolio, alerts, notification prefs (`LocalDataRepository`)
- `"app_settings_prefs"` — theme, signal config, day-trader settings (`SettingsRepository`)

All data serialized as JSON via Gson. Use `TypeToken` for list types.

## Domain / Analysis Engines
All engines in `domain/` are Kotlin `object` singletons taking `List<ChartEntry>` + `TechnicalData` as input:
- `TechnicalAnalysis` — SMA, EMA, RSI, MACD, Bollinger Bands, ATR, ADX, etc.
- `ConfluenceScoreEngine` — 0–100 score; weights: EMA 20 | MACD 20 | RSI 20 | Volume 15 | ATR 10 | ADX 15
- `SmartSignalEngine` — weighted 0–100; Trend 35% | Momentum 25% | Volume 15% | Volatility 15% | Trend Strength 10%
- `BacktestEngine`, `RiskCalculator`, `StockRankingEngine`, `DayTraderSignalEngine`, `OrderFlowSimulator`

Always call `TechnicalAnalysis.calculateAllIndicators(entries)` to produce `TechnicalData` before passing to any engine.

## Background Tasks (WorkManager)
- `PriceAlertWorker` — runs every 15 min (network required); checks active price alerts + top news headlines
- `MarketSessionWorker` / `MarketNotificationScheduler` — fires market open/close notifications
- Both are scheduled in `MainActivity.onCreate()`. Use `ExistingPeriodicWorkPolicy.KEEP` to avoid duplicate enqueue.

## Caching
Repositories use in-memory caches (not persisted across process death):
- `StockRepository`: `quoteCache`, `chartCache` (Maps)
- `CryptoRepository`: `ConcurrentHashMap` shared across instances via `companion object`
- `CommodityRepository`: 1-hour TTL cache (`Constants.COMMODITY_CACHE_TTL_MS = 3_600_000L`)

