package com.cookbook.app.data.models

import java.io.Serializable

/**
 * User model matching the backend API
 */
data class User(
    val id: String,
    val username: String,
    val email: String? = null,
    val role: String = "user",
    val googleId: String? = null,
    val avatar: String? = null,
    val twoFactorEnabled: Boolean = false,
    val createdAt: String = ""
) : Serializable {
    
    val isAdmin: Boolean
        get() = role == "admin"
    
    val displayName: String
        get() = username
}

/**
 * Login request model
 */
data class LoginRequest(
    val username: String,
    val password: String,
    val twoFactorCode: String? = null
)

/**
 * Google login request model
 */
data class GoogleLoginRequest(
    val credential: String,
    val twoFactorCode: String? = null
)

/**
 * Login response model
 */
data class LoginResponse(
    val token: String? = null,
    val user: User? = null,
    val requires2FA: Boolean = false,
    val userId: String? = null,
    val message: String? = null
)

/**
 * Generic API error response
 */
data class ApiError(
    val error: String,
    val message: String? = null
)
