package com.cookbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.BuildConfig
import com.cookbook.app.R
import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.repository.AuthRepository
import com.cookbook.app.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

/**
 * Login activity with Google Sign-In support and server URL configuration
 */
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val authRepository by lazy { AuthRepository() }
    private lateinit var credentialManager: CredentialManager
    
    // For 2FA flow
    private var pendingUserId: String? = null
    private var pendingGoogleCredential: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        credentialManager = CredentialManager.create(this)
        
        // Load local background image
        binding.ivBackground.setImageResource(R.drawable.login_background)
        binding.ivBackground.scaleType = ImageView.ScaleType.CENTER_CROP
        
        // Initialize API client
        ApiClient.initialize(this)
        
        setupUI()
        checkInitialState()
    }
    
    private fun setupUI() {
        // Server URL save button
        binding.btnSaveUrl.setOnClickListener {
            saveServerUrl()
        }
        
        // Change server button
        binding.btnChangeServer.setOnClickListener {
            showServerSetup()
        }
        
        // Login button
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            
            if (validateInput(username, password)) {
                performLogin(username, password)
            }
        }
        
        // Google Sign-In button
        binding.btnGoogleSignIn.setOnClickListener {
            performGoogleSignIn()
        }
        
        // 2FA submit button
        binding.btnSubmit2FA.setOnClickListener {
            val code = binding.et2FACode.text.toString().trim()
            if (code.length == 6) {
                submit2FACode(code)
            } else {
                binding.et2FACode.error = getString(R.string.field_required)
            }
        }
        
        // Cancel 2FA
        binding.btnCancel2FA.setOnClickListener {
            hide2FAInput()
        }
    }
    
    private fun checkInitialState() {
        val tokenManager = ApiClient.getTokenManager()
        
        if (!tokenManager.isApiUrlConfigured()) {
            // No server URL configured - show setup
            showServerSetup()
        } else {
            // Server URL exists - show login form
            showLoginForm()
            
            // Check if already logged in
            if (authRepository.isLoggedIn()) {
                verifyExistingLogin()
            }
        }
    }
    
    private fun showServerSetup() {
        binding.serverUrlContainer.visibility = View.VISIBLE
        binding.loginFormContainer.visibility = View.GONE
        binding.twoFactorContainer.visibility = View.GONE
        
        // Pre-fill with current URL if exists (remove /api suffix for display)
        val currentUrl = ApiClient.getCurrentBaseUrl()
        if (currentUrl != null) {
            val displayUrl = currentUrl.removeSuffix("/api").removeSuffix("/")
            binding.etServerUrl.setText(displayUrl)
        }
    }
    
    private fun showLoginForm() {
        binding.serverUrlContainer.visibility = View.GONE
        binding.loginFormContainer.visibility = View.VISIBLE
        binding.twoFactorContainer.visibility = View.GONE
        
        // Show current server URL
        val currentUrl = ApiClient.getCurrentBaseUrl()
        if (currentUrl != null) {
            binding.serverInfoContainer.visibility = View.VISIBLE
            binding.tvServerUrl.text = currentUrl
        } else {
            binding.serverInfoContainer.visibility = View.GONE
        }
    }
    
    private fun saveServerUrl() {
        var url = binding.etServerUrl.text.toString().trim()
        
        // Basic URL validation
        if (url.isEmpty() || !url.startsWith("http")) {
            binding.tilServerUrl.error = getString(R.string.invalid_url)
            return
        }
        
        // Remove trailing slash for consistency
        url = url.trimEnd('/')
        
        // Automatically append /api if not present
        if (!url.endsWith("/api")) {
            url = "$url/api"
        }
        
        binding.tilServerUrl.error = null
        setLoading(true)
        
        // Test connection and save URL
        lifecycleScope.launch {
            try {
                // Initialize API client with new URL
                ApiClient.initializeWithUrl(this@LoginActivity, url)
                
                // Save URL to settings
                ApiClient.getTokenManager().saveApiUrl(url)
                
                setLoading(false)
                showLoginForm()
                
            } catch (e: Exception) {
                setLoading(false)
                showError(getString(R.string.connection_error))
            }
        }
    }
    
    private fun verifyExistingLogin() {
        lifecycleScope.launch {
            setLoading(true)
            val result = authRepository.getCurrentUser()
            setLoading(false)
            
            result.onSuccess {
                navigateToMain()
            }.onFailure {
                // Token invalid, stay on login screen
                lifecycleScope.launch {
                    authRepository.logout()
                }
            }
        }
    }
    
    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true
        
        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.field_required)
            isValid = false
        } else {
            binding.tilUsername.error = null
        }
        
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.field_required)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }
        
        return isValid
    }
    
    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch {
            setLoading(true)
            
            val result = authRepository.login(username, password)
            
            setLoading(false)
            
            result.onSuccess { response ->
                when {
                    response.requires2FA == true -> {
                        pendingUserId = response.userId
                        pendingGoogleCredential = null
                        show2FAInput()
                    }
                    response.token != null -> {
                        navigateToMain()
                    }
                    else -> {
                        showError(response.message ?: "Login failed")
                    }
                }
            }.onFailure { error ->
                showError(error.message ?: "Login failed")
            }
        }
    }
    
    private fun performGoogleSignIn() {
        Log.d(TAG, "Starting Google Sign-In with Client ID: ${BuildConfig.GOOGLE_CLIENT_ID.take(20)}...")
        
        lifecycleScope.launch {
            setLoading(true)
            
            try {
                // Clear any stale credential state first to ensure fresh sign-in
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    Log.d(TAG, "Cleared credential state before sign-in")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear credential state: ${e.message}")
                    // Continue anyway, this is not critical
                }
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .setAutoSelectEnabled(false) // Always show account picker
                    .setNonce(generateNonce())
                    .build()
                
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleGoogleSignInResult(result)
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Google Sign-In cancelled by user")
                setLoading(false)
                // User cancelled, no error message needed
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google credentials available", e)
                setLoading(false)
                showError(getString(R.string.google_signin_no_account))
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-In failed: ${e.type} - ${e.message}", e)
                setLoading(false)
                showError(getString(R.string.google_signin_failed))
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Google Sign-In", e)
                setLoading(false)
                showError("${getString(R.string.google_signin_failed)}: ${e.message}")
            }
        }
    }
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    private fun handleGoogleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        
        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        
                        // Send to backend
                        lifecycleScope.launch {
                            val loginResult = authRepository.googleLogin(idToken)
                            
                            setLoading(false)
                            
                            loginResult.onSuccess { response ->
                                when {
                                    response.requires2FA == true -> {
                                        pendingUserId = response.userId
                                        pendingGoogleCredential = idToken
                                        show2FAInput()
                                    }
                                    response.token != null -> {
                                        navigateToMain()
                                    }
                                    else -> {
                                        showError(response.message ?: "Google Login failed")
                                    }
                                }
                            }.onFailure { error ->
                                showError(error.message ?: "Google Login failed")
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        setLoading(false)
                        showError("Could not process Google token")
                    }
                }
            }
            else -> {
                setLoading(false)
                showError("Unknown credential type")
            }
        }
    }
    
    private fun submit2FACode(code: String) {
        lifecycleScope.launch {
            setLoading(true)
            
            val result = if (pendingGoogleCredential != null) {
                authRepository.googleLogin(pendingGoogleCredential!!, code)
            } else {
                val username = binding.etUsername.text.toString().trim()
                val password = binding.etPassword.text.toString()
                authRepository.login(username, password, code)
            }
            
            setLoading(false)
            
            result.onSuccess { response ->
                if (response.token != null) {
                    navigateToMain()
                } else {
                    showError(response.message ?: "2FA verification failed")
                }
            }.onFailure { error ->
                showError(error.message ?: "2FA verification failed")
            }
        }
    }
    
    private fun show2FAInput() {
        binding.loginFormContainer.visibility = View.GONE
        binding.serverUrlContainer.visibility = View.GONE
        binding.twoFactorContainer.visibility = View.VISIBLE
        binding.et2FACode.text?.clear()
        binding.et2FACode.requestFocus()
    }
    
    private fun hide2FAInput() {
        binding.twoFactorContainer.visibility = View.GONE
        binding.loginFormContainer.visibility = View.VISIBLE
        pendingUserId = null
        pendingGoogleCredential = null
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogleSignIn.isEnabled = !isLoading
        binding.btnSubmit2FA.isEnabled = !isLoading
        binding.btnSaveUrl.isEnabled = !isLoading
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
