package com.cookbook.app

import android.app.Application
import android.util.Log
import com.cookbook.app.data.api.ApiClient
import kotlinx.coroutines.runBlocking

/**
 * Application class for Cookbook app.
 * Performs network detection and API client initialization.
 */
class CookbookApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize API client synchronously to ensure it's ready before any Activity starts
        // Network detection happens here
        runBlocking {
            try {
                ApiClient.initializeAsync(this@CookbookApplication)
                Log.i(TAG, "API client initialized successfully")
                
                if (ApiClient.isUsingInternalNetwork()) {
                    Log.i(TAG, "📍 Running in INTERNAL network (Port 3003)")
                } else {
                    Log.i(TAG, "🌐 Running in EXTERNAL network (Standard port)")
                }
                
                Log.i(TAG, "Base URL: ${ApiClient.getCurrentBaseUrl()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize API client", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "CookbookApp"
    }
}
