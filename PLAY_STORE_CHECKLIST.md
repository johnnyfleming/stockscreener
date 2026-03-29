# 🚀 Google Play Store Launch Checklist — TradeScreener AI

Track your progress: check off items as you complete them.

---

## Stage 1: Security & API Key Migration
- [x] Move API keys from hardcoded `build.gradle.kts` to `local.properties`
- [x] Update `app/build.gradle.kts` to read keys via `Properties().load()`
- [x] Update `.gitignore` — added `*.jks`, `*.keystore`, `keystore.properties`, `google-services.json`
- [x] Create `network_security_config.xml` — blocks cleartext (HTTP) traffic
- [x] Reference `networkSecurityConfig` in `AndroidManifest.xml`
- [x] Gate OkHttp logging on `BuildConfig.DEBUG` (Level.NONE in release)
- [x] **⚠️ MANUAL**: Rotate all 4 API keys — DONE:
  - [x] AlphaVantage: `044NJJSAVQZVQ0GX`
  - [x] Finnhub: `d74g319r01qno4q1kti0d74g319r01qno4q1ktig`
  - [x] NewsAPI: `dc102566d9e948a2aabea8438cbe0078`
  - [x] Polygon: `nwTs6EYPDar1S7dAmqbn6jOJRWqDtYlb`
  - [x] New keys updated in `local.properties`

---

## Stage 2: App Identity & Signing
- [x] **DECISION**: `applicationId = "com.tradescreenerai.app"` — set in `app/build.gradle.kts`
- [x] **MANUAL**: Release keystore generated — `tradescreener-release.jks` (alias: `tradescreener`, password: `tradescreener2026`)
- [x] `keystore.properties` updated with actual passwords
- [x] Signing config added to `app/build.gradle.kts` (reads from `keystore.properties`)

---

## Stage 3: Release Build Hardening
- [x] Enable `isMinifyEnabled = true` and `isShrinkResources = true` in release build type
- [x] ProGuard rules added for: Retrofit, OkHttp, Gson, data models, Compose, Coroutines, DataStore, Crashlytics, WorkManager, Coil
- [x] Line number preservation enabled for Crashlytics stack traces

---

## Stage 4: Firebase Integration
- [x] Firebase plugins added to root `build.gradle.kts`
- [x] Firebase plugins applied in `app/build.gradle.kts`
- [x] Firebase BoM + Analytics + Crashlytics added to version catalog & dependencies
- [x] **MANUAL**: Firebase Console setup DONE — project "TradeScreener AI", app `com.tradescreenerai.app`, real `google-services.json` in `app/`
- [ ] **OPTIONAL**: Add Crashlytics custom keys in `MainActivity.kt` for richer crash reports

---

## Stage 5: Privacy & Compliance
- [x] `backup_rules.xml` updated — excludes DataStore preference files
- [x] `data_extraction_rules.xml` updated — excludes DataStore from cloud backup & device transfer
- [x] Privacy Policy drafted (`PRIVACY_POLICY.md`)
- [x] **MANUAL**: `PRIVACY_POLICY.md` email updated → `jhbfleming@gmail.com`
- [ ] **MANUAL**: Host privacy policy online (options):
  - Firebase Hosting (free)
  - GitHub Pages (free)
  - Notion public page (free)
  - Any static website host
- [ ] **MANUAL**: Prepare Google Play Data Safety form answers:
  - ✅ No PII collected
  - ✅ No data shared with third parties for advertising
  - ✅ No user accounts or login
  - ✅ Network calls to financial APIs for display only
  - ✅ Firebase Crashlytics collects anonymous crash data
  - ✅ Firebase Analytics collects anonymous usage data
  - ✅ Data encrypted in transit (HTTPS)
  - ✅ No data deletion mechanism needed (no server-side data)

---

## Stage 6: Play Store Listing Preparation
- [ ] **Short description** (≤80 chars):
  > AI-powered stock, crypto & commodity screener with smart trading signals.
- [ ] **Full description** (≤4000 chars) — cover these features:
  - Real-time stock/crypto/commodity market screener
  - AI-powered Confluence Score (0–100) & Smart Signal Engine
  - Technical analysis: SMA, EMA, RSI, MACD, Bollinger Bands, ATR, ADX
  - Day Trader mode with order flow simulation
  - Backtesting engine for strategy validation
  - Portfolio tracking & watchlists
  - Price alerts with background notifications
  - Multi-source news feed
  - Market session (open/close) notifications
  - Fear & Greed Index
  - Penny stock discovery
  - Stock ranking engine
  - Dark/Light/System theme
- [ ] **Feature graphic** (1024×500 PNG) — branded banner
- [ ] **Screenshots** (min 2, recommended 4–8):
  - [ ] Dashboard
  - [ ] Stock Detail / Chart
  - [ ] Screener with filters
  - [ ] Smart Signals
  - [ ] Day Trader mode
  - [ ] Crypto screen
  - [ ] Commodities screen
  - [ ] Portfolio / Watchlist
- [ ] **Hi-res icon** (512×512 PNG) — verify `ic_launcher-playstore.png` meets spec
- [ ] **Content Rating questionnaire** — Finance/utility, no violence/gambling → PEGI 3 / Everyone
- [ ] **App category**: Finance | Tags: Stocks, Cryptocurrency, Investing

---

## Stage 7: Build, Test & Publish
- [x] Build signed release AAB — **DONE** (7.5 MB, built 29/03/2026):
  Output: `app/build/outputs/bundle/release/app-release.aab`
- [ ] Install release APK on device & smoke-test all screens:
  ```powershell
  .\gradlew installRelease
  ```
  - [ ] Dashboard loads stock data
  - [ ] Stock Detail shows chart + indicators
  - [ ] Screener filters work
  - [ ] Signals compute correctly
  - [ ] Day Trader mode functions
  - [ ] Crypto data loads
  - [ ] Commodities data loads
  - [ ] Portfolio add/remove works
  - [ ] Watchlist add/remove works
  - [ ] Price alerts fire (test with 15-min worker)
  - [ ] News feed displays
  - [ ] Settings (theme) persists
  - [ ] No crashes in logcat
  - [ ] OkHttp logs show NONE (not BODY)
- [ ] **MANUAL**: Google Play Console (https://play.google.com/console):
  1. Create new app
  2. Fill in Store Listing (description, screenshots, graphic, icon)
  3. Set privacy policy URL
  4. Complete Data Safety form
  5. Complete Content Rating questionnaire
  6. Set target audience & countries
  7. Upload AAB to **Internal Testing** track first
  8. Test via opt-in link on 1–2 devices
  9. Promote to **Production** (or Closed Beta first)
  10. Submit for review

---

## Stage 8: Post-Launch Maintenance
- [ ] Enable **Google Play App Signing** (offered on first AAB upload — recommended)
- [ ] Monitor Firebase Crashlytics dashboard
- [ ] Set up Crashlytics email alerts for new crash clusters
- [ ] Monitor Firebase Analytics for screen_view engagement
- [ ] Set up Play Console alerts for ANRs, bad ratings, policy violations
- [ ] **For each update**:
  - Increment `versionCode` by 1
  - Bump `versionName` (e.g., 1.0.0 → 1.0.1)
  - Build new AAB → upload to Play Console
- [ ] **OPTIONAL**: Set up GitHub Actions CI/CD:
  - Auto-build on push
  - Auto-bundle on tag
  - Store keystore + API keys in GitHub Secrets

---

## Files Modified/Created in This Migration

| File | Action | Purpose |
|------|--------|---------|
| `local.properties` | Modified | API keys moved here |
| `app/build.gradle.kts` | Modified | Read keys from props, signing config, R8 enabled, Firebase plugins |
| `build.gradle.kts` (root) | Modified | Firebase plugins added |
| `gradle/libs.versions.toml` | Modified | Firebase dependencies added |
| `.gitignore` | Modified | Keystore, secrets, google-services exclusions |
| `app/proguard-rules.pro` | Replaced | Production ProGuard rules |
| `app/src/main/AndroidManifest.xml` | Modified | networkSecurityConfig added |
| `app/src/main/res/xml/network_security_config.xml` | Created | Block cleartext traffic |
| `app/src/main/res/xml/backup_rules.xml` | Modified | Exclude DataStore from backup |
| `app/src/main/res/xml/data_extraction_rules.xml` | Modified | Exclude DataStore from extraction |
| `data/remote/RetrofitClient.kt` | Modified | Logging gated on DEBUG |
| `keystore.properties` | Created | Template for release signing |
| `PRIVACY_POLICY.md` | Created | Privacy policy draft |
| `PLAY_STORE_CHECKLIST.md` | Created | This file |

