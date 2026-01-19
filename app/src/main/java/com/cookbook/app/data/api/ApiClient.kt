package com.cookbook.app.data.api

import android.content.Context
import android.util.Log
import com.cookbook.app.BuildConfig
import com.cookbook.app.data.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Singleton API client for backend communication.
 * Automatically detects internal/external network and uses the appropriate URL.
 */
object ApiClient {
    
    private const val TAG = "ApiClient"
    
    private var tokenManager: TokenManager? = null
    private var retrofit: Retrofit? = null
    private var api: CookbookApi? = null
    private var currentBaseUrl: String? = null
    
    /**
     * Initialize the API client with context for token management.
     * This performs network detection to determine the correct API URL.
     */
    suspend fun initializeAsync(context: Context) {
        tokenManager = TokenManager(context)
        
        // Detect network and get appropriate URL
        val baseUrl = NetworkDetector.getApiBaseUrl()
        
        if (currentBaseUrl != baseUrl || retrofit == null) {
            currentBaseUrl = baseUrl
            val isInternal = baseUrl == BuildConfig.API_URL_INTERNAL
            retrofit = createRetrofit(baseUrl, trustAllCerts = isInternal)
            api = retrofit?.create(CookbookApi::class.java)
            Log.i(TAG, "API client initialized with URL: $baseUrl (internal=$isInternal)")
        }
    }
    
    /**
     * Initialize synchronously (for cases where coroutine isn't available).
     * Uses cached URL or runs blocking detection.
     */
    fun initialize(context: Context) {
        tokenManager = TokenManager(context)
        
        // Use cached URL or run blocking detection
        val baseUrl = NetworkDetector.getCachedUrl() ?: runBlocking {
            NetworkDetector.getApiBaseUrl()
        }
        
        if (currentBaseUrl != baseUrl || retrofit == null) {
            currentBaseUrl = baseUrl
            val isInternal = baseUrl == BuildConfig.API_URL_INTERNAL
            retrofit = createRetrofit(baseUrl, trustAllCerts = isInternal)
            api = retrofit?.create(CookbookApi::class.java)
            Log.i(TAG, "API client initialized with URL: $baseUrl (internal=$isInternal)")
        }
    }
    
    /**
     * Reinitialize with fresh network detection.
     * Call this when network conditions might have changed.
     */
    suspend fun reinitialize(context: Context) {
        NetworkDetector.invalidateCache()
        initializeAsync(context)
    }
    
    /**
     * Get the API instance.
     */
    fun getApi(): CookbookApi {
        return api ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }
    
    /**
     * Get the token manager.
     */
    fun getTokenManager(): TokenManager {
        return tokenManager ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }
    
    /**
     * Get the current base URL being used.
     */
    fun getCurrentBaseUrl(): String? = currentBaseUrl
    
    /**
     * Check if using internal network.
     */
    fun isUsingInternalNetwork(): Boolean = NetworkDetector.isUsingInternalNetwork()
    
    private fun createRetrofit(baseUrl: String, trustAllCerts: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // Longer for image uploads
        
        // For internal network: Trust all certificates (Synology self-signed)
        if (trustAllCerts) {
            Log.w(TAG, "⚠️ Trusting all certificates for internal network")
            configureTrustAllCertificates(clientBuilder)
        }
        
        // Ensure URL ends with /
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Configures OkHttp to trust all SSL certificates.
     * ONLY used for internal network where Synology uses self-signed certs.
     */
    private fun configureTrustAllCertificates(builder: OkHttpClient.Builder) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            
            Log.d(TAG, "SSL trust-all configured for internal network")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure trust-all SSL", e)
        }
    }
    
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            
            // Get token synchronously (blocking call for interceptor)
            val token = tokenManager?.getTokenSync()
            
            val request = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .build()
            } else {
                originalRequest.newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
            }
            
            chain.proceed(request)
        }
    }
    
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            // Truncate very long messages (base64 images)
            val truncated = if (message.length > 1000) {
                "${message.take(500)}... [truncated ${message.length - 500} chars]"
            } else {
                message
            }
            Log.d("OkHttp", truncated)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
}
