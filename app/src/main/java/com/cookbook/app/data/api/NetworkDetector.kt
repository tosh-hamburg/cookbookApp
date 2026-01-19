package com.cookbook.app.data.api

import android.util.Log
import com.cookbook.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Detects whether the app is running in the internal or external network
 * by checking if the internal API endpoint is reachable.
 */
object NetworkDetector {
    
    private const val TAG = "NetworkDetector"
    private const val CHECK_TIMEOUT_MS = 3000L
    
    private var cachedBaseUrl: String? = null
    private var lastCheckTime: Long = 0
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 Minuten Cache
    
    /**
     * Determines the correct API base URL based on network availability.
     * Results are cached for 5 minutes.
     */
    suspend fun getApiBaseUrl(): String {
        // Return cached result if still valid
        val now = System.currentTimeMillis()
        cachedBaseUrl?.let { url ->
            if (now - lastCheckTime < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached URL: $url")
                return url
            }
        }
        
        // Check if internal network is reachable
        val isInternal = isInternalNetworkReachable()
        
        val baseUrl = if (isInternal) {
            Log.i(TAG, "✓ Internal network detected - using Synology URL")
            BuildConfig.API_URL_INTERNAL
        } else {
            Log.i(TAG, "✓ External network detected - using public URL")
            BuildConfig.API_URL_EXTERNAL
        }
        
        // Cache the result
        cachedBaseUrl = baseUrl
        lastCheckTime = now
        
        return baseUrl
    }
    
    /**
     * Checks if the internal API endpoint is reachable.
     * Uses simple TCP socket connection to check reachability (no SSL verification needed).
     */
    private suspend fun isInternalNetworkReachable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                    val host = BuildConfig.INTERNAL_HOST
                    val port = BuildConfig.INTERNAL_PORT
                    
                    Log.d(TAG, "Checking internal network: $host:$port")
                    
                    // Simple TCP socket connection test (doesn't need SSL)
                    checkHostReachable(host, port)
                }
                val reachable = result == true
                Log.d(TAG, "Internal network reachable: $reachable")
                reachable
            } catch (e: Exception) {
                Log.d(TAG, "Internal network check failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Checks if a host:port is reachable using a simple TCP socket connection.
     */
    private fun checkHostReachable(host: String, port: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CHECK_TIMEOUT_MS.toInt())
            Log.d(TAG, "TCP connection to $host:$port successful")
            true
        } catch (e: Exception) {
            Log.d(TAG, "TCP connection to $host:$port failed: ${e.message}")
            false
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Forces a refresh of the network detection on next call.
     */
    fun invalidateCache() {
        cachedBaseUrl = null
        lastCheckTime = 0
        Log.d(TAG, "Network detection cache invalidated")
    }
    
    /**
     * Returns the currently cached URL or null if not yet determined.
     */
    fun getCachedUrl(): String? = cachedBaseUrl
    
    /**
     * Checks if we're currently using the internal network.
     */
    fun isUsingInternalNetwork(): Boolean {
        return cachedBaseUrl == BuildConfig.API_URL_INTERNAL
    }
}
