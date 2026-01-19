package com.cookbook.app.data.repository

import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.models.*

/**
 * Repository for authentication operations
 */
class AuthRepository {
    
    private val api by lazy { ApiClient.getApi() }
    private val tokenManager by lazy { ApiClient.getTokenManager() }
    
    /**
     * Login with username and password
     */
    suspend fun login(username: String, password: String, twoFactorCode: String? = null): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(username, password, twoFactorCode))
            if (response.isSuccessful) {
                val loginResponse = response.body()!!
                
                // If login successful (has token), save credentials
                if (loginResponse.token != null && loginResponse.user != null) {
                    tokenManager.saveToken(loginResponse.token)
                    tokenManager.saveUser(loginResponse.user)
                }
                
                Result.success(loginResponse)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Login fehlgeschlagen"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Login with Google credential
     */
    suspend fun googleLogin(credential: String, twoFactorCode: String? = null): Result<LoginResponse> {
        return try {
            val response = api.googleLogin(GoogleLoginRequest(credential, twoFactorCode))
            if (response.isSuccessful) {
                val loginResponse = response.body()!!
                
                // If login successful (has token), save credentials
                if (loginResponse.token != null && loginResponse.user != null) {
                    tokenManager.saveToken(loginResponse.token)
                    tokenManager.saveUser(loginResponse.user)
                }
                
                Result.success(loginResponse)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Google Login fehlgeschlagen"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current user from API
     */
    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful) {
                val user = response.body()!!
                tokenManager.saveUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Benutzer konnte nicht geladen werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get cached user
     */
    fun getCachedUser(): User? {
        return tokenManager.getUserSync()
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedInSync()
    }
    
    /**
     * Logout - clear all auth data
     */
    suspend fun logout() {
        tokenManager.clearAll()
    }
}
