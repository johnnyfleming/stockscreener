package com.tradescreenerai.app.data.repository

import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.data.model.NewsArticle
import com.tradescreenerai.app.data.model.NewsSentiment
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository {

    private val newsApi      = RetrofitClient.newsApi
    private val finnhubApi   = RetrofitClient.finnhubApi
    private val alphaApi     = RetrofitClient.alphaVantageApi
    private val newsApiKey   = BuildConfig.NEWS_API_KEY
    private val finnhubKey   = BuildConfig.FINNHUB_KEY
    private val alphaKey     = BuildConfig.ALPHA_VANTAGE_KEY
    private var cachedNews: List<NewsArticle> = emptyList()

    // ── Sentiment keyword scoring ──────────────────────────────────────────────
    private fun analyzeSentiment(text: String): NewsSentiment {
        val lower = text.lowercase()
        val positiveWords = listOf("surge", "rise", "gain", "rally", "profit", "bullish",
            "growth", "record", "jump", "soar", "strong", "positive", "beat", "upgrade",
            "buy", "bull", "high", "boom", "up", "outperform", "top", "exceed", "boost")
        val negativeWords = listOf("crash", "fall", "loss", "drop", "bear", "bearish",
            "decline", "low", "sell", "weak", "negative", "collapse", "plunge", "warn",
            "miss", "downgrade", "cut", "risk", "concern", "fear", "down", "slump", "recession")
        val posScore = positiveWords.count { lower.contains(it) }
        val negScore = negativeWords.count { lower.contains(it) }
        return when {
            posScore > negScore + 1 -> NewsSentiment.POSITIVE
            negScore > posScore + 1 -> NewsSentiment.NEGATIVE
            else                    -> NewsSentiment.NEUTRAL
        }
    }

    suspend fun getMarketNews(): Resource<List<NewsArticle>> = withContext(Dispatchers.IO) {
        // ── 1. Finnhub general news (free, works on Android) ──────────────────
        try {
            val result = getFinnhubNews()
            if (result is Resource.Success && (result.data?.size ?: 0) >= 5) {
                return@withContext result
            }
        } catch (_: Exception) { }

        // ── 2. Alpha Vantage NEWS_SENTIMENT (free, no mobile restriction) ─────
        try {
            val result = getAlphaVantageNews()
            if (result is Resource.Success && (result.data?.size ?: 0) >= 3) {
                return@withContext result
            }
        } catch (_: Exception) { }

        // ── 3. NewsAPI.org (may be blocked on Android, but worth a try) ───────
        try {
            val response  = newsApi.getTopHeadlines(apiKey = newsApiKey)
            val articles  = response.getAsJsonArray("articles")
            if (articles != null && articles.size() > 0) {
                val news = (0 until articles.size()).mapNotNull { i ->
                    val item  = articles[i].asJsonObject
                    val title = item.get("title")?.asString
                    if (title != null && title != "[Removed]") {
                        val desc = item.get("description")?.asString ?: ""
                        NewsArticle(
                            title       = title,
                            description = desc,
                            url         = item.get("url")?.asString ?: "",
                            imageUrl    = if (item.get("urlToImage")?.isJsonNull == false)
                                            item.get("urlToImage")?.asString else null,
                            source      = item.getAsJsonObject("source")?.get("name")?.asString ?: "",
                            publishedAt = item.get("publishedAt")?.asString ?: "",
                            content     = item.get("content")?.asString,
                            sentiment   = analyzeSentiment("$title $desc")
                        )
                    } else null
                }
                if (news.isNotEmpty()) {
                    cachedNews = news
                    return@withContext Resource.Success(news)
                }
            }
        } catch (_: Exception) { }

        // ── 4. Return cache if all sources failed ─────────────────────────────
        if (cachedNews.isNotEmpty()) Resource.Success(cachedNews)
        else Resource.Error("Unable to load news. Check your connection.")
    }

    private suspend fun getFinnhubNews(): Resource<List<NewsArticle>> {
        val response = finnhubApi.getMarketNews(token = finnhubKey)
        if (response.size() == 0) return Resource.Error("No Finnhub news")
        val news = (0 until minOf(response.size(), 40)).map { i ->
            val item     = response[i].asJsonObject
            val headline = item.get("headline")?.asString ?: ""
            val summary  = item.get("summary")?.asString  ?: ""
            NewsArticle(
                title       = headline,
                description = summary,
                url         = item.get("url")?.asString  ?: "",
                imageUrl    = item.get("image")?.asString?.takeIf { it.isNotBlank() },
                source      = item.get("source")?.asString ?: "Finnhub",
                publishedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .format(java.util.Date((item.get("datetime")?.asLong ?: 0L) * 1000L)),
                sentiment   = analyzeSentiment("$headline $summary")
            )
        }
        cachedNews = news
        return Resource.Success(news)
    }

    private suspend fun getAlphaVantageNews(): Resource<List<NewsArticle>> {
        val response = alphaApi.getNewsSentiment(apiKey = alphaKey)
        val feedArr  = response.getAsJsonArray("feed") ?: return Resource.Error("No AV news")
        val news = (0 until minOf(feedArr.size(), 40)).mapNotNull { i ->
            try {
                val item    = feedArr[i].asJsonObject
                val title   = item.get("title")?.asString ?: return@mapNotNull null
                val summary = item.get("summary")?.asString ?: ""
                val url     = item.get("url")?.asString ?: ""
                val source  = item.get("source")?.asString ?: "Alpha Vantage"
                val time    = item.get("time_published")?.asString ?: ""
                // AV sentiment: Bullish/Somewhat-Bullish/Neutral/Somewhat-Bearish/Bearish
                val avSentiment = item.get("overall_sentiment_label")?.asString ?: ""
                val sentiment = when {
                    avSentiment.contains("Bullish", ignoreCase = true)  -> NewsSentiment.POSITIVE
                    avSentiment.contains("Bearish", ignoreCase = true)  -> NewsSentiment.NEGATIVE
                    else -> analyzeSentiment("$title $summary")
                }
                // Parse image from banner_image field
                val imageUrl = item.get("banner_image")?.asString?.takeIf { it.isNotBlank() }
                // Convert AV time format "20240327T123000" → readable
                val publishedAt = try {
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
                    val outSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    outSdf.format(sdf.parse(time) ?: java.util.Date())
                } catch (_: Exception) { time }
                NewsArticle(
                    title       = title,
                    description = summary,
                    url         = url,
                    imageUrl    = imageUrl,
                    source      = source,
                    publishedAt = publishedAt,
                    sentiment   = sentiment
                )
            } catch (_: Exception) { null }
        }
        if (news.isEmpty()) return Resource.Error("No AV news items")
        cachedNews = news
        return Resource.Success(news)
    }

    suspend fun searchNews(query: String): Resource<List<NewsArticle>> = withContext(Dispatchers.IO) {
        // Try Alpha Vantage topic search first (works on Android)
        try {
            val response = alphaApi.getCompanyNewsSentiment(tickers = query.uppercase(), apiKey = alphaKey)
            val feedArr  = response.getAsJsonArray("feed")
            if (feedArr != null && feedArr.size() > 0) {
                val news = (0 until minOf(feedArr.size(), 30)).mapNotNull { i ->
                    try {
                        val item    = feedArr[i].asJsonObject
                        val title   = item.get("title")?.asString ?: return@mapNotNull null
                        val summary = item.get("summary")?.asString ?: ""
                        val avSentiment = item.get("overall_sentiment_label")?.asString ?: ""
                        val sentiment = when {
                            avSentiment.contains("Bullish", ignoreCase = true)  -> NewsSentiment.POSITIVE
                            avSentiment.contains("Bearish", ignoreCase = true)  -> NewsSentiment.NEGATIVE
                            else -> analyzeSentiment("$title $summary")
                        }
                        val time = item.get("time_published")?.asString ?: ""
                        val publishedAt = try {
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
                            val outSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            outSdf.format(sdf.parse(time) ?: java.util.Date())
                        } catch (_: Exception) { time }
                        NewsArticle(
                            title       = title,
                            description = summary,
                            url         = item.get("url")?.asString ?: "",
                            imageUrl    = item.get("banner_image")?.asString?.takeIf { it.isNotBlank() },
                            source      = item.get("source")?.asString ?: "Alpha Vantage",
                            publishedAt = publishedAt,
                            sentiment   = sentiment
                        )
                    } catch (_: Exception) { null }
                }
                if (news.isNotEmpty()) return@withContext Resource.Success(news)
            }
        } catch (_: Exception) { }

        // Fallback: NewsAPI.org
        try {
            val response = newsApi.getEverything(query = query, apiKey = newsApiKey)
            val articles = response.getAsJsonArray("articles")
            val news = if (articles != null) {
                (0 until articles.size()).mapNotNull { i ->
                    val item  = articles[i].asJsonObject
                    val title = item.get("title")?.asString
                    if (title != null && title != "[Removed]") {
                        val desc = item.get("description")?.asString ?: ""
                        NewsArticle(
                            title       = title,
                            description = desc,
                            url         = item.get("url")?.asString ?: "",
                            imageUrl    = if (item.get("urlToImage")?.isJsonNull == false)
                                            item.get("urlToImage")?.asString else null,
                            source      = item.getAsJsonObject("source")?.get("name")?.asString ?: "",
                            publishedAt = item.get("publishedAt")?.asString ?: "",
                            sentiment   = analyzeSentiment("$title $desc")
                        )
                    } else null
                }
            } else emptyList()
            Resource.Success(news)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompanyNews(symbol: String): Resource<List<NewsArticle>> = withContext(Dispatchers.IO) {
        // ── 1. Alpha Vantage company news (reliable on Android) ───────────────
        try {
            val response = alphaApi.getCompanyNewsSentiment(tickers = symbol, apiKey = alphaKey)
            val feedArr  = response.getAsJsonArray("feed")
            if (feedArr != null && feedArr.size() > 0) {
                val news = (0 until minOf(feedArr.size(), 20)).mapNotNull { i ->
                    try {
                        val item    = feedArr[i].asJsonObject
                        val title   = item.get("title")?.asString ?: return@mapNotNull null
                        val summary = item.get("summary")?.asString ?: ""
                        val avSentiment = item.get("overall_sentiment_label")?.asString ?: ""
                        val sentiment = when {
                            avSentiment.contains("Bullish", ignoreCase = true)  -> NewsSentiment.POSITIVE
                            avSentiment.contains("Bearish", ignoreCase = true)  -> NewsSentiment.NEGATIVE
                            else -> analyzeSentiment("$title $summary")
                        }
                        val time = item.get("time_published")?.asString ?: ""
                        val publishedAt = try {
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
                            val outSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            outSdf.format(sdf.parse(time) ?: java.util.Date())
                        } catch (_: Exception) { time }
                        NewsArticle(
                            title       = title,
                            description = summary,
                            url         = item.get("url")?.asString ?: "",
                            imageUrl    = item.get("banner_image")?.asString?.takeIf { it.isNotBlank() },
                            source      = item.get("source")?.asString ?: "Alpha Vantage",
                            publishedAt = publishedAt,
                            sentiment   = sentiment
                        )
                    } catch (_: Exception) { null }
                }
                if (news.isNotEmpty()) return@withContext Resource.Success(news)
            }
        } catch (_: Exception) { }

        // ── 2. Finnhub company news fallback ─────────────────────────────────
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val to  = sdf.format(java.util.Date())
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -14)
            val from = sdf.format(cal.time)
            val response = finnhubApi.getCompanyNews(symbol, from, to, finnhubKey)
            val news = (0 until minOf(response.size(), 20)).map { i ->
                val item     = response[i].asJsonObject
                val headline = item.get("headline")?.asString ?: ""
                val summary  = item.get("summary")?.asString  ?: ""
                NewsArticle(
                    title       = headline,
                    description = summary,
                    url         = item.get("url")?.asString ?: "",
                    imageUrl    = item.get("image")?.asString?.takeIf { it.isNotBlank() },
                    source      = item.get("source")?.asString ?: "Finnhub",
                    publishedAt = sdf.format(java.util.Date((item.get("datetime")?.asLong ?: 0L) * 1000L)),
                    sentiment   = analyzeSentiment("$headline $summary")
                )
            }
            Resource.Success(news)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }
}
