package com.cookbook.app

import android.app.Application
import android.util.Log
import com.cookbook.app.data.api.ApiClient

/**
 * Application class for Cookbook app.
 * Initializes API client with stored URL from settings.
 */
class CookbookApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize API client with stored URL (if configured)
        try {
            ApiClient.initialize(this)
            
            val baseUrl = ApiClient.getCurrentBaseUrl()
            if (baseUrl != null) {
                Log.i(TAG, "API client initialized with URL: $baseUrl")
            } else {
                Log.i(TAG, "API client initialized - no URL configured yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize API client", e)
        }
    }
    
    companion object {
        private const val TAG = "CookbookApp"
    }
}
