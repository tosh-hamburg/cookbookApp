package com.cookbook.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.cookbook.app.R
import com.cookbook.app.data.models.Ingredient
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityRecipeDetailBinding
import com.cookbook.app.ui.adapter.ImagePagerAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/**
 * Activity showing recipe details
 */
class RecipeDetailActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
    }
    
    private lateinit var binding: ActivityRecipeDetailBinding
    private val recipeRepository by lazy { RecipeRepository() }
    
    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null
    private var currentServings: Int = 4
    private var originalServings: Int = 4
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        
        setupToolbar()
        setupServingsControls()
        
        if (recipeId != null) {
            loadRecipe()
        } else {
            showError("Rezept-ID fehlt")
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh when returning from edit
        recipeId?.let { loadRecipe() }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
    
    private fun setupServingsControls() {
        binding.btnDecreaseServings.setOnClickListener {
            if (currentServings > 1) {
                currentServings--
                updateServingsDisplay()
                updateIngredientsDisplay()
            }
        }
        
        binding.btnIncreaseServings.setOnClickListener {
            if (currentServings < 99) {
                currentServings++
                updateServingsDisplay()
                updateIngredientsDisplay()
            }
        }
        
        binding.btnSendToGemini.setOnClickListener {
            sendToGemini()
        }
    }
    
    private fun updateServingsDisplay() {
        binding.tvServingsCount.text = currentServings.toString()
        
        val portionLabel = if (currentServings == 1) {
            getString(R.string.portion_singular)
        } else {
            getString(R.string.portion_plural)
        }
        binding.tvServingsLabel.text = portionLabel
        
        // Show hint if different from original
        if (currentServings != originalServings) {
            val originalLabel = if (originalServings == 1) {
                getString(R.string.portion_singular)
            } else {
                getString(R.string.portion_plural)
            }
            binding.tvOriginalServings.text = getString(
                R.string.original_recipe_for,
                originalServings,
                originalLabel
            )
            binding.tvOriginalServings.visibility = View.VISIBLE
        } else {
            binding.tvOriginalServings.visibility = View.GONE
        }
    }
    
    private fun updateIngredientsDisplay() {
        currentRecipe?.let { recipe ->
            val scaledIngredients = getScaledIngredients(recipe.ingredients)
            binding.tvIngredients.text = scaledIngredients.joinToString("\n") { ingredient ->
                "• ${ingredient.amount} ${ingredient.name}"
            }
        }
    }
    
    /**
     * Scale ingredients based on current vs original servings
     */
    private fun getScaledIngredients(ingredients: List<Ingredient>): List<Ingredient> {
        val factor = currentServings.toDouble() / originalServings.toDouble()
        return ingredients.map { ing ->
            Ingredient(
                name = ing.name,
                amount = scaleIngredientAmount(ing.amount, factor)
            )
        }
    }
    
    /**
     * Scale an ingredient amount string by a factor
     * Handles numbers, decimals, and fractions (like 1/2)
     */
    private fun scaleIngredientAmount(amount: String, factor: Double): String {
        if (amount.isBlank() || factor == 1.0) return amount
        
        // Regex to match numbers (including decimals with . or , and fractions like 1/2)
        val numberPattern = Regex("""(\d+(?:[.,]\d+)?(?:\s*/\s*\d+)?)""")
        
        return numberPattern.replace(amount) { match ->
            val matchStr = match.value
            
            if (matchStr.contains("/")) {
                // Handle fractions like "1/2"
                val parts = matchStr.split("/").map { it.trim().replace(",", ".").toDoubleOrNull() ?: 1.0 }
                if (parts.size == 2) {
                    val value = (parts[0] / parts[1]) * factor
                    formatScaledNumber(value)
                } else {
                    matchStr
                }
            } else {
                // Handle regular numbers
                val num = matchStr.replace(",", ".").toDoubleOrNull() ?: return@replace matchStr
                val scaled = num * factor
                formatScaledNumber(scaled)
            }
        }
    }
    
    /**
     * Format a scaled number nicely, using fractions when appropriate
     */
    private fun formatScaledNumber(value: Double): String {
        // Check if it's a whole number
        if (value == floor(value)) {
            return value.toInt().toString()
        }
        
        val intPart = floor(value).toInt()
        val remainder = value % 1
        
        // Check for common fractions
        return when {
            abs(remainder - 0.5) < 0.01 -> if (intPart > 0) "$intPart½" else "½"
            abs(remainder - 0.25) < 0.01 -> if (intPart > 0) "$intPart¼" else "¼"
            abs(remainder - 0.75) < 0.01 -> if (intPart > 0) "$intPart¾" else "¾"
            abs(remainder - 0.333) < 0.02 -> if (intPart > 0) "$intPart⅓" else "⅓"
            abs(remainder - 0.666) < 0.02 -> if (intPart > 0) "$intPart⅔" else "⅔"
            else -> {
                // Round to 1 decimal place
                val formatted = "%.1f".format(value).replace(".", ",").trimEnd('0').trimEnd(',')
                formatted
            }
        }
    }
    
    private fun loadRecipe() {
        lifecycleScope.launch {
            setLoading(true)
            
            val result = recipeRepository.getRecipe(recipeId!!)
            
            setLoading(false)
            
            result.onSuccess { recipe ->
                currentRecipe = recipe
                displayRecipe(recipe)
            }.onFailure { error ->
                showError(error.message ?: "Rezept konnte nicht geladen werden")
                finish()
            }
        }
    }
    
    private fun displayRecipe(recipe: Recipe) {
        // Title
        supportActionBar?.title = recipe.title
        binding.tvTitle.text = recipe.title
        
        // Images
        if (recipe.images.isNotEmpty()) {
            binding.viewPagerImages.visibility = View.VISIBLE
            binding.tabLayoutIndicator.visibility = if (recipe.images.size > 1) View.VISIBLE else View.GONE
            
            val adapter = ImagePagerAdapter(recipe.images)
            binding.viewPagerImages.adapter = adapter
            
            TabLayoutMediator(binding.tabLayoutIndicator, binding.viewPagerImages) { _, _ -> }
                .attach()
        } else {
            binding.viewPagerImages.visibility = View.GONE
            binding.tabLayoutIndicator.visibility = View.GONE
        }
        
        // Time info
        binding.tvPrepTime.text = "${recipe.prepTime} Min."
        binding.tvCookTime.text = "${recipe.cookTime} Min."
        binding.tvTotalTime.text = "${recipe.totalTime} Min."
        
        // Initialize servings
        originalServings = recipe.servings.takeIf { it > 0 } ?: 4
        currentServings = originalServings
        updateServingsDisplay()
        
        // Calories
        if (recipe.caloriesPerUnit > 0) {
            binding.tvCalories.visibility = View.VISIBLE
            binding.tvCalories.text = "${recipe.caloriesPerUnit} kcal/${recipe.weightUnit}"
        } else {
            binding.tvCalories.visibility = View.GONE
        }
        
        // Categories
        binding.chipGroupCategories.removeAllViews()
        recipe.categoryNames.forEach { categoryName ->
            val chip = Chip(this).apply {
                text = categoryName
                isClickable = false
            }
            binding.chipGroupCategories.addView(chip)
        }
        
        // Ingredients (with potential scaling)
        updateIngredientsDisplay()
        
        // Instructions
        binding.tvInstructions.text = recipe.instructions
        
        // Source URL
        if (!recipe.sourceUrl.isNullOrEmpty()) {
            binding.btnSourceUrl.visibility = View.VISIBLE
            binding.btnSourceUrl.setOnClickListener {
                openUrl(recipe.sourceUrl)
            }
        } else {
            binding.btnSourceUrl.visibility = View.GONE
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            showError("URL konnte nicht geöffnet werden")
        }
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.contentContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // ==================== Menu ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recipe_detail, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_edit -> {
                openEditActivity()
                true
            }
            R.id.action_collections -> {
                showManageCollections()
                true
            }
            R.id.action_gemini -> {
                sendToGemini()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            R.id.action_share -> {
                shareRecipe()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showManageCollections() {
        currentRecipe?.let { recipe ->
            val bottomSheet = ManageCollectionsBottomSheet.newInstance(recipe)
            bottomSheet.onCollectionsUpdated = {
                // Reload recipe to get updated collections
                loadRecipe()
            }
            bottomSheet.show(supportFragmentManager, "ManageCollections")
        }
    }
    
    private fun sendToGemini() {
        currentRecipe?.let { recipe ->
            // Get scaled ingredients based on current servings
            val scaledIngredients = getScaledIngredients(recipe.ingredients)
            
            // Format ingredients as a list
            val ingredientsList = scaledIngredients
                .map { if (it.amount.isNotEmpty()) "${it.amount} ${it.name}" else it.name }
                .joinToString("\n")
            
            // Portion label
            val portionLabel = if (currentServings == 1) {
                getString(R.string.portion_singular)
            } else {
                getString(R.string.portion_plural)
            }
            
            // Create Gemini prompt (same as web app)
            val prompt = """Füge bitte folgende Zutaten zu meiner Einkaufsliste in Google Keep hinzu (erstelle die Liste "Einkaufsliste" falls sie nicht existiert):

${recipe.title} ($currentServings $portionLabel):
$ingredientsList"""
            
            // Copy to clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Gemini Prompt", prompt)
            clipboard.setPrimaryClip(clip)
            
            // Show dialog with option to open Gemini
            AlertDialog.Builder(this)
                .setTitle(R.string.gemini_prompt_copied)
                .setMessage(R.string.gemini_prompt_description)
                .setPositiveButton(R.string.open_gemini) { _, _ ->
                    openGemini()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
    
    private fun openGemini() {
        android.util.Log.d("RecipeDetail", "openGemini called")
        
        // Try to open Gemini app directly first
        val geminiPackages = listOf(
            "com.google.android.apps.bard",      // Gemini app
            "com.google.android.apps.googleassistant"  // Google Assistant with Gemini
        )
        
        for (packageName in geminiPackages) {
            try {
                android.util.Log.d("RecipeDetail", "Trying package: $packageName")
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    android.util.Log.d("RecipeDetail", "Found package, launching: $packageName")
                    startActivity(launchIntent)
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeDetail", "Error with package $packageName", e)
            }
        }
        
        // Fallback: Open Gemini website in browser with chooser
        android.util.Log.d("RecipeDetail", "No Gemini app found, opening browser")
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app"))
            val chooser = Intent.createChooser(browserIntent, "Gemini öffnen mit...")
            startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("RecipeDetail", "Error opening browser", e)
            showError(getString(R.string.gemini_not_found))
        }
    }
    
    private fun openEditActivity() {
        currentRecipe?.let { recipe ->
            val intent = Intent(this, RecipeEditActivity::class.java)
            intent.putExtra(RecipeEditActivity.EXTRA_RECIPE_ID, recipe.id)
            startActivity(intent)
        }
    }
    
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recipe)
            .setMessage(R.string.delete_recipe_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteRecipe()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deleteRecipe() {
        recipeId?.let { id ->
            lifecycleScope.launch {
                setLoading(true)
                
                val result = recipeRepository.deleteRecipe(id)
                
                result.onSuccess {
                    Toast.makeText(this@RecipeDetailActivity, R.string.recipe_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure { error ->
                    setLoading(false)
                    showError(error.message ?: "Rezept konnte nicht gelöscht werden")
                }
            }
        }
    }
    
    private fun shareRecipe() {
        currentRecipe?.let { recipe ->
            val shareText = buildString {
                appendLine(recipe.title)
                appendLine()
                appendLine("Zutaten:")
                recipe.ingredients.forEach { ingredient ->
                    appendLine("• ${ingredient.amount} ${ingredient.name}")
                }
                appendLine()
                appendLine("Zubereitung:")
                appendLine(recipe.instructions)
                
                if (!recipe.sourceUrl.isNullOrEmpty()) {
                    appendLine()
                    appendLine("Quelle: ${recipe.sourceUrl}")
                }
            }
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, recipe.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Rezept teilen"))
        }
    }
}
