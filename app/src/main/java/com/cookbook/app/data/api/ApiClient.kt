package com.cookbook.app.data.api

import android.content.Context
import android.util.Log
import com.cookbook.app.BuildConfig
import com.cookbook.app.data.auth.TokenManager
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
 * Uses user-configured API URL stored in settings.
 */
object ApiClient {
    
    private const val TAG = "ApiClient"
    
    private var tokenManager: TokenManager? = null
    private var retrofit: Retrofit? = null
    private var api: CookbookApi? = null
    private var currentBaseUrl: String? = null
    
    /**
     * Initialize the API client with context for token management.
     * Uses the stored API URL from settings.
     */
    fun initialize(context: Context) {
        tokenManager = TokenManager(context)
        
        val baseUrl = tokenManager?.getApiUrlSync()
        if (baseUrl != null) {
            setupRetrofit(baseUrl)
        }
    }
    
    /**
     * Initialize with a specific URL (used during login setup)
     */
    fun initializeWithUrl(context: Context, baseUrl: String) {
        tokenManager = TokenManager(context)
        setupRetrofit(baseUrl)
    }
    
    /**
     * Update the API URL and reinitialize
     */
    fun updateApiUrl(baseUrl: String) {
        setupRetrofit(baseUrl)
    }
    
    private fun setupRetrofit(baseUrl: String) {
        if (currentBaseUrl != baseUrl || retrofit == null) {
            currentBaseUrl = baseUrl
            // Trust all certs for HTTPS URLs (self-signed certificates support)
            val trustAllCerts = baseUrl.startsWith("https://")
            retrofit = createRetrofit(baseUrl, trustAllCerts = trustAllCerts)
            api = retrofit?.create(CookbookApi::class.java)
            Log.i(TAG, "API client initialized with URL: $baseUrl")
        }
    }
    
    /**
     * Get the API instance.
     */
    fun getApi(): CookbookApi {
        return api ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first or configure API URL.")
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
     * Check if API is configured and ready
     */
    fun isConfigured(): Boolean = api != null && currentBaseUrl != null
    
    private fun createRetrofit(baseUrl: String, trustAllCerts: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // Longer for image uploads
        
        // For HTTPS: Trust all certificates (self-signed support)
        if (trustAllCerts) {
            Log.w(TAG, "⚠️ Trusting all certificates for HTTPS connection")
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
     * Used for self-signed certificates.
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
            
            Log.d(TAG, "SSL trust-all configured")
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
            val truncated = if (message.length > 2000) {
                "${message.take(1000)}... [truncated ${message.length - 1000} chars]"
            } else {
                message
            }
            Log.d("OkHttp", truncated)
        }.apply {
            // Use BODY for debugging meal plan issues
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
}
