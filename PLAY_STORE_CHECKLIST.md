# 🚀 Google Play Store Launch Checklist — TradeScreener AI

---

## 📊 Status Summary

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | API keys rotated & moved to `local.properties` | ✅ Done | All 4 keys updated |
| 2 | `applicationId` set to `com.tradescreenerai.app` | ✅ Done | Permanent — cannot change after publish |
| 3 | Release keystore generated | ✅ Done | `tradescreener-release.jks`, password: `tradescreener2026` |
| 4 | Signing config wired in `build.gradle.kts` | ✅ Done | Reads from `keystore.properties` |
| 5 | R8 minification + ProGuard rules | ✅ Done | |
| 6 | Network security config (blocks HTTP) | ✅ Done | |
| 7 | Firebase + Crashlytics integrated | ✅ Done | Real `google-services.json` in place |
| 8 | Privacy policy written & pushed to GitHub | ✅ Done | URL in Key Info section below |
| 9 | Signed release AAB built (7.5 MB) | ✅ Done | `app/build/outputs/bundle/release/app-release.aab` |
| 10 | **Feature graphic** (1024×500 PNG) | ⬜ TODO | Make on canva.com — dark background, app name + chart |
| 11 | **Screenshots** (4–8) | ⬜ TODO | Run `.\gradlew installRelease`, screenshot key screens |
| 12 | **Verify app icon** (512×512 PNG) | ⬜ TODO | Check `res/mipmap-xxxhdpi/ic_launcher-playstore.png` |
| 13 | **Smoke-test release build on device** | ⬜ TODO | `.\gradlew installRelease` — check all screens, no crashes |
| 14 | **Create app in Google Play Console** | ⬜ TODO | https://play.google.com/console |
| 15 | **Upload AAB to Internal Testing track** | ⬜ TODO | Use AAB from step 9 |
| 16 | **Fill Store Listing** | ⬜ TODO | Descriptions ready below — copy/paste |
| 17 | **Set privacy policy URL** | ⬜ TODO | URL in Key Info section below |
| 18 | **Complete Data Safety form** | ⬜ TODO | Answers ready below |
| 19 | **Complete Content Rating questionnaire** | ⬜ TODO | Finance/utility → Everyone / PEGI 3 |
| 20 | **Submit for review** | ⬜ TODO | After internal test passes |

---

## 🔑 Key Info (Keep Safe)

| Item | Value |
|------|-------|
| Application ID | `com.tradescreenerai.app` |
| Keystore file | `tradescreener-release.jks` (project root) |
| Key alias | `tradescreener` |
| Keystore / key password | `tradescreener2026` |
| Privacy policy URL | https://github.com/johnnyfleming/stockscreener/blob/master/PRIVACY_POLICY.md |
| AAB location | `app/build/outputs/bundle/release/app-release.aab` |
| Current version | 1.0.0 (versionCode 1) |

---

## 📋 Copy-Paste Ready: Store Listing

### Short Description (≤ 80 chars)
```
AI-powered stock, crypto & commodity screener with smart trading signals.
```

### Full Description (≤ 4000 chars)
```
TradeScreener AI is a powerful, real-time market screener for stocks, cryptocurrencies, and commodities — built for traders who want an edge.

KEY FEATURES

📈 Smart Signal Engine
AI-powered Confluence Score (0–100) combines trend, momentum, volume, volatility, and trend strength to generate clear BUY / SELL / HOLD signals.

📊 Full Technical Analysis
SMA, EMA, RSI, MACD, Bollinger Bands, ATR, ADX and more — all calculated automatically from live price data.

⚡ Day Trader Mode
Real-time intraday signals with order flow simulation and momentum indicators designed for fast-moving markets.

🔍 Market Screener
Screen hundreds of stocks, cryptos, and commodities with customisable filters. Find opportunities instantly.

🧪 Backtesting Engine
Test your strategy against historical data before risking real money.

💼 Portfolio & Watchlist
Track your holdings and favourite assets in one place. Add price alerts that notify you in the background.

🌍 Multi-Market Coverage
• Stocks — real-time quotes, charts, earnings data
• Cryptocurrencies — top coins via CoinGecko
• Commodities — Gold, Silver, Oil, Natural Gas, Copper and more

📰 News Feed
Multi-source financial news aggregated in one feed, keeping you informed on market-moving events.

📉 Fear & Greed Index
Gauge overall market sentiment at a glance.

🔔 Smart Notifications
Market open/close alerts and price target notifications powered by background workers.

🎨 Themes
Dark, Light, and System theme — easy on the eyes during long trading sessions.

No account required. No subscription. Free to use.
```

### App Category
**Finance** | Tags: `Stocks`, `Cryptocurrency`, `Investing`, `Trading`

---

## 🔒 Data Safety Form Answers

| Question | Answer |
|----------|--------|
| Does your app collect or share user data? | Yes (anonymous only) |
| Location data collected? | No |
| Personal info (name, email, etc.) collected? | No |
| Financial info collected? | No |
| App activity / usage data collected? | Yes — Firebase Analytics (anonymous) |
| Crash logs collected? | Yes — Firebase Crashlytics (anonymous) |
| Data shared with third parties for advertising? | No |
| Data used to track users across apps? | No |
| Data encrypted in transit? | Yes (HTTPS only) |
| User can request data deletion? | Not applicable — no server-side user data stored |

---

## 📱 Screenshots To Capture

Run `.\gradlew installRelease` then take screenshots of:

| Screen | What to show |
|--------|-------------|
| Dashboard | Live stock prices loading |
| Stock Detail | Chart + RSI/MACD indicators visible |
| Screener | Filter panel open with results |
| Smart Signals | Confluence score cards |
| Day Trader | Intraday chart + signal bar |
| Crypto | Coin list with live prices |
| Commodities | Gold/Oil/Silver price cards |
| Portfolio | Holdings list |

---

## ⏭️ After Publishing

| Task | When |
|------|------|
| Enable Google Play App Signing (offered on first upload) | Immediately |
| Monitor Crashlytics for crash clusters | Ongoing |
| Monitor Play Console for ANRs / bad ratings | Ongoing |
| Per update: bump `versionCode` + `versionName`, build new AAB, upload | Each release |
