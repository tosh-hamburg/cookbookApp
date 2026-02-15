package com.cookbook.app.data.api

import android.content.Context
import android.util.Log
import com.cookbook.app.BuildConfig
import com.cookbook.app.data.auth.TokenManager
import com.cookbook.app.data.models.LoginResponse
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createTokenRefreshInterceptor(normalizedUrl))
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // Longer for image uploads
        
        // For HTTPS: Trust all certificates (self-signed support)
        if (trustAllCerts) {
            Log.w(TAG, "⚠️ Trusting all certificates for HTTPS connection")
            configureTrustAllCertificates(clientBuilder)
        }
        
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
    
    private val refreshLock = Any()
    @Volatile private var isRefreshing = false

    private fun createTokenRefreshInterceptor(baseUrl: String): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)

            // Only attempt refresh on 401/403, skip for auth endpoints
            val path = originalRequest.url.encodedPath
            if ((response.code == 401 || response.code == 403) &&
                !path.contains("auth/login") &&
                !path.contains("auth/google") &&
                !path.contains("auth/refresh")
            ) {
                val token = tokenManager?.getTokenSync()
                if (token != null) {
                    synchronized(refreshLock) {
                        // Double-check: another thread may have refreshed already
                        val currentToken = tokenManager?.getTokenSync()
                        if (currentToken != null && currentToken == token && !isRefreshing) {
                            isRefreshing = true
                            try {
                                val refreshRequest = Request.Builder()
                                    .url("${baseUrl}auth/refresh")
                                    .post("{}".toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", "Bearer $token")
                                    .build()

                                val refreshClientBuilder = OkHttpClient.Builder()
                                    .connectTimeout(10, TimeUnit.SECONDS)
                                    .readTimeout(10, TimeUnit.SECONDS)
                                if (baseUrl.startsWith("https://")) {
                                    configureTrustAllCertificates(refreshClientBuilder)
                                }
                                val refreshClient = refreshClientBuilder.build()

                                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                                if (refreshResponse.isSuccessful) {
                                    val body = refreshResponse.body?.string()
                                    val loginResponse = Gson().fromJson(body, LoginResponse::class.java)
                                    if (loginResponse?.token != null) {
                                        kotlinx.coroutines.runBlocking {
                                            tokenManager?.saveToken(loginResponse.token)
                                            loginResponse.user?.let { tokenManager?.saveUser(it) }
                                        }
                                        Log.i(TAG, "Token refreshed successfully")
                                    }
                                } else {
                                    Log.w(TAG, "Token refresh failed: ${refreshResponse.code}")
                                }
                                refreshResponse.close()
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }

                    // Retry original request with new token
                    val newToken = tokenManager?.getTokenSync()
                    if (newToken != null && newToken != token) {
                        response.close()
                        val retryRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                        return@Interceptor chain.proceed(retryRequest)
                    }
                }
            }

            response
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
