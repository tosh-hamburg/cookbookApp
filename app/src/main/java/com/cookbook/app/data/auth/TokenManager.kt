package com.cookbook.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cookbook.app.data.models.User
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Manages authentication tokens and user data using DataStore
 */
class TokenManager(private val context: Context) {
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_KEY = stringPreferencesKey("current_user")
    }
    
    private val gson = Gson()
    
    // ==================== Token ====================
    
    /**
     * Save the authentication token
     */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }
    
    /**
     * Get the authentication token as a Flow
     */
    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }
    
    /**
     * Get the token synchronously (for interceptors)
     */
    fun getTokenSync(): String? {
        return runBlocking {
            context.dataStore.data.first()[TOKEN_KEY]
        }
    }
    
    /**
     * Clear the authentication token
     */
    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }
    
    // ==================== User ====================
    
    /**
     * Save the current user
     */
    suspend fun saveUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[USER_KEY] = gson.toJson(user)
        }
    }
    
    /**
     * Get the current user as a Flow
     */
    val userFlow: Flow<User?> = context.dataStore.data.map { preferences ->
        preferences[USER_KEY]?.let { json ->
            try {
                gson.fromJson(json, User::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get the current user synchronously
     */
    fun getUserSync(): User? {
        return runBlocking {
            context.dataStore.data.first()[USER_KEY]?.let { json ->
                try {
                    gson.fromJson(json, User::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Clear the current user
     */
    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_KEY)
        }
    }
    
    // ==================== Combined ====================
    
    /**
     * Check if user is logged in
     */
    val isLoggedInFlow: Flow<Boolean> = tokenFlow.map { it != null }
    
    /**
     * Check if user is logged in synchronously
     */
    fun isLoggedInSync(): Boolean {
        return getTokenSync() != null
    }
    
    /**
     * Clear all auth data (logout)
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USER_KEY)
        }
    }
}
