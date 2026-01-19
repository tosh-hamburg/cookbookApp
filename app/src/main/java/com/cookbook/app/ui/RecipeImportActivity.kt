package com.cookbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.R
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityRecipeImportBinding
import kotlinx.coroutines.launch

/**
 * Activity for importing recipes from URLs
 */
class RecipeImportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRecipeImportBinding
    private val recipeRepository by lazy { RecipeRepository() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        
        // Check for shared URL intent
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                        // Extract URL from shared text
                        val urlRegex = Regex("https?://[\\S]+")
                        val match = urlRegex.find(sharedText)
                        match?.value?.let { url ->
                            binding.etUrl.setText(url)
                        }
                    }
                }
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Rezept importieren"
    }
    
    private fun setupUI() {
        binding.btnImport.setOnClickListener {
            importRecipe()
        }
        
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun importRecipe() {
        val url = binding.etUrl.text.toString().trim()
        
        if (url.isEmpty()) {
            binding.tilUrl.error = "URL erforderlich"
            return
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            binding.tilUrl.error = "Ungültige URL"
            return
        }
        
        binding.tilUrl.error = null
        
        lifecycleScope.launch {
            setLoading(true)
            binding.tvStatus.text = "Rezept wird importiert..."
            binding.tvStatus.visibility = View.VISIBLE
            
            val result = recipeRepository.importRecipe(url)
            
            setLoading(false)
            
            result.onSuccess { importedData ->
                binding.tvStatus.text = "Rezept \"${importedData.title}\" erfolgreich importiert!"
                
                // Open edit activity with imported data
                val intent = Intent(this@RecipeImportActivity, RecipeEditActivity::class.java)
                intent.putExtra(RecipeEditActivity.EXTRA_IMPORTED_DATA, importedData)
                startActivity(intent)
                finish()
                
            }.onFailure { error ->
                binding.tvStatus.text = "Fehler: ${error.message}"
                showError(error.message ?: "Import fehlgeschlagen")
            }
        }
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnImport.isEnabled = !isLoading
        binding.btnCancel.isEnabled = !isLoading
        binding.etUrl.isEnabled = !isLoading
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
