package com.cookbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.BuildConfig
import com.cookbook.app.R
import com.cookbook.app.data.repository.AuthRepository
import com.cookbook.app.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

/**
 * Login activity with Google Sign-In support
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
        
        // Load local background image (same as web app)
        // Image file: res/drawable-nodpi/login_background.jpg
        binding.ivBackground.setImageResource(R.drawable.login_background)
        binding.ivBackground.scaleType = ImageView.ScaleType.CENTER_CROP
        
        setupUI()
        checkExistingLogin()
    }
    
    private fun setupUI() {
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
                binding.et2FACode.error = "Bitte 6-stelligen Code eingeben"
            }
        }
        
        // Cancel 2FA
        binding.btnCancel2FA.setOnClickListener {
            hide2FAInput()
        }
    }
    
    private fun checkExistingLogin() {
        if (authRepository.isLoggedIn()) {
            // Verify token is still valid
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
                        showError(response.message ?: "Login fehlgeschlagen")
                    }
                }
            }.onFailure { error ->
                showError(error.message ?: "Login fehlgeschlagen")
            }
        }
    }
    
    private fun performGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setNonce(generateNonce())
            .build()
        
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        
        lifecycleScope.launch {
            setLoading(true)
            
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleGoogleSignInResult(result)
            } catch (e: GetCredentialException) {
                setLoading(false)
                showError("Google Sign-In fehlgeschlagen: ${e.message}")
            }
        }
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
                                        showError(response.message ?: "Google Login fehlgeschlagen")
                                    }
                                }
                            }.onFailure { error ->
                                showError(error.message ?: "Google Login fehlgeschlagen")
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        setLoading(false)
                        showError("Google Token konnte nicht verarbeitet werden")
                    }
                }
            }
            else -> {
                setLoading(false)
                showError("Unbekannter Credential-Typ")
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
                    showError(response.message ?: "2FA-Verifizierung fehlgeschlagen")
                }
            }.onFailure { error ->
                showError(error.message ?: "2FA-Verifizierung fehlgeschlagen")
            }
        }
    }
    
    private fun show2FAInput() {
        binding.loginFormContainer.visibility = View.GONE
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
