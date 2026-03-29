package com.tradescreenerai.app.data.remote

import com.tradescreenerai.app.data.remote.api.AlphaVantageApi
import com.tradescreenerai.app.data.remote.api.AlternativeMeApi
import com.tradescreenerai.app.data.remote.api.BinanceApi
import com.tradescreenerai.app.data.remote.api.CoinCapApi
import com.tradescreenerai.app.data.remote.api.CoinGeckoApi
import com.tradescreenerai.app.data.remote.api.CoinPaprikaApi
import com.tradescreenerai.app.data.remote.api.FinnhubApi
import com.tradescreenerai.app.data.remote.api.NewsApiService
import com.tradescreenerai.app.data.remote.api.PolygonApi
import com.tradescreenerai.app.data.remote.api.YahooFinanceApi
import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Dedicated OkHttp client for Yahoo Finance.
     * Yahoo's CDN requires a browser-like User-Agent and Accept header.
     */
    private val yahooHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Origin", "https://finance.yahoo.com")
                    .header("Referer", "https://finance.yahoo.com/")
                    .header("Cache-Control", "no-cache")
                    .build()
            )
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val alphaVantageApi: AlphaVantageApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALPHA_VANTAGE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlphaVantageApi::class.java)
    }

    val coinGeckoApi: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.COINGECKO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    val coinPaprikaApi: CoinPaprikaApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.COINPAPRIKA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinPaprikaApi::class.java)
    }

    val coinCapApi: CoinCapApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.COINCAP_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinCapApi::class.java)
    }

    val finnhubApi: FinnhubApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.FINNHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinnhubApi::class.java)
    }

    val newsApi: NewsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.NEWS_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApiService::class.java)
    }

    val alternativeMeApi: AlternativeMeApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALTERNATIVE_ME_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlternativeMeApi::class.java)
    }

    val polygonApi: PolygonApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.POLYGON_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PolygonApi::class.java)
    }

    val binanceApi: BinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BINANCE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApi::class.java)
    }

    val yahooFinanceApi: YahooFinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.YAHOO_FINANCE_BASE_URL)
            .client(yahooHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YahooFinanceApi::class.java)
    }
}
