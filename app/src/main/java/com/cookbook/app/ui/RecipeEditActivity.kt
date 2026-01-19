package com.cookbook.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookbook.app.R
import com.cookbook.app.data.models.*
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityRecipeEditBinding
import com.cookbook.app.ui.adapter.EditImageAdapter
import com.cookbook.app.ui.adapter.EditIngredientAdapter
import com.cookbook.app.util.ImageUtils
import com.google.android.material.chip.Chip
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activity for creating and editing recipes
 */
class RecipeEditActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RecipeEditActivity"
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
        const val EXTRA_IMPORTED_DATA = "extra_imported_data"
    }
    
    private lateinit var binding: ActivityRecipeEditBinding
    private val recipeRepository by lazy { RecipeRepository() }
    private lateinit var imageAdapter: EditImageAdapter
    private lateinit var ingredientAdapter: EditIngredientAdapter
    
    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null
    private var categories: List<String> = emptyList()
    private var selectedCategories: MutableSet<String> = mutableSetOf()
    
    // Camera capture URI
    private var currentPhotoUri: Uri? = null
    
    // Activity Result Launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchGallery()
        } else {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            processImage(currentPhotoUri!!)
        }
    }
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        
        setupToolbar()
        setupUI()
        
        // Load data - categories first, then recipe/import data
        loadData()
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            setLoading(true)
            
            // First load categories
            val categoriesResult = recipeRepository.getCategories()
            categoriesResult.onSuccess { loadedCategories ->
                categories = loadedCategories
            }
            
            // Then load recipe or import data
            if (recipeId != null) {
                val recipeResult = recipeRepository.getRecipe(recipeId!!)
                setLoading(false)
                
                recipeResult.onSuccess { recipe ->
                    currentRecipe = recipe
                    populateForm(recipe)
                }.onFailure { error ->
                    showError(error.message ?: "Rezept konnte nicht geladen werden")
                    finish()
                }
            } else {
                setLoading(false)
                
                // Check for imported data
                @Suppress("DEPRECATION")
                val importedData = intent.getSerializableExtra(EXTRA_IMPORTED_DATA) as? ImportedRecipeData
                if (importedData != null) {
                    populateFromImport(importedData)
                } else {
                    // New recipe - just setup empty category chips
                    setupCategoryChips()
                }
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = if (recipeId != null) {
            getString(R.string.edit_recipe)
        } else {
            getString(R.string.add_recipe)
        }
    }
    
    private fun setupUI() {
        // Add ingredient button
        binding.btnAddIngredient.setOnClickListener {
            addIngredient()
        }
        
        // Save button
        binding.btnSave.setOnClickListener {
            saveRecipe()
        }
        
        // Cancel button
        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        // Image picker buttons
        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }
        
        binding.btnChooseFromGallery.setOnClickListener {
            checkStoragePermissionAndLaunch()
        }
        
        // Image RecyclerView
        imageAdapter = EditImageAdapter { position ->
            imageAdapter.removeImage(position)
            updateImagesVisibility()
        }
        binding.recyclerViewImages.apply {
            layoutManager = LinearLayoutManager(this@RecipeEditActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }
        
        // Ingredient RecyclerView
        ingredientAdapter = EditIngredientAdapter(
            onEditClick = { position, ingredient ->
                showEditIngredientDialog(position, ingredient)
            },
            onDeleteClick = { position ->
                ingredientAdapter.removeIngredient(position)
                updateIngredientsVisibility()
            }
        )
        binding.recyclerViewIngredients.apply {
            layoutManager = LinearLayoutManager(this@RecipeEditActivity)
            adapter = ingredientAdapter
        }
        
        // Clear all ingredients button
        binding.btnClearIngredients.setOnClickListener {
            ingredientAdapter.clearAll()
            updateIngredientsVisibility()
        }
    }
    
    private fun showEditIngredientDialog(position: Int, ingredient: IngredientRequest) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_ingredient, null)
        val etAmount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDialogAmount)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDialogName)
        
        // Pre-fill with current values
        etAmount.setText(ingredient.amount)
        etName.setText(ingredient.name)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.edit_ingredient)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newAmount = etAmount.text.toString().trim()
                val newName = etName.text.toString().trim()
                
                if (newAmount.isNotEmpty() && newName.isNotEmpty()) {
                    ingredientAdapter.updateIngredient(position, IngredientRequest(newName, newAmount))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun checkStoragePermissionAndLaunch() {
        // On Android 13+, we don't need storage permission for picking images
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchGallery()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12: scoped storage, no permission needed for picker
            launchGallery()
        } else {
            // Below Android 10, need READ_EXTERNAL_STORAGE
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                    launchGallery()
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    private fun launchCamera() {
        val photoFile = createImageFile()
        currentPhotoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(currentPhotoUri)
    }
    
    private fun launchGallery() {
        pickImageLauncher.launch("image/*")
    }
    
    private fun createImageFile(): File {
        val imagesDir = File(cacheDir, "images")
        imagesDir.mkdirs()
        return File.createTempFile("recipe_", ".jpg", imagesDir)
    }
    
    private fun processImage(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@RecipeEditActivity, R.string.adding_image, Toast.LENGTH_SHORT).show()
            
            val base64DataUrl = withContext(Dispatchers.IO) {
                ImageUtils.uriToBase64DataUrl(this@RecipeEditActivity, uri)
            }
            
            if (base64DataUrl != null) {
                imageAdapter.addImage(base64DataUrl)
                updateImagesVisibility()
                Toast.makeText(this@RecipeEditActivity, R.string.image_added, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@RecipeEditActivity, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateImagesVisibility() {
        val hasImages = imageAdapter.itemCount > 0
        binding.tvNoImages.visibility = if (hasImages) View.GONE else View.VISIBLE
        binding.recyclerViewImages.visibility = if (hasImages) View.VISIBLE else View.GONE
    }
    
    private fun setupCategoryChips() {
        binding.chipGroupCategories.removeAllViews()
        
        categories.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = selectedCategories.contains(category)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategories.add(category)
                    } else {
                        selectedCategories.remove(category)
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }
    
    private fun populateForm(recipe: Recipe) {
        binding.etTitle.setText(recipe.title)
        binding.etInstructions.setText(recipe.instructions)
        binding.etPrepTime.setText(recipe.prepTime.toString())
        binding.etRestTime.setText(recipe.restTime.toString())
        binding.etCookTime.setText(recipe.cookTime.toString())
        binding.etServings.setText(recipe.servings.toString())
        binding.etCalories.setText(if (recipe.caloriesPerUnit > 0) recipe.caloriesPerUnit.toString() else "")
        binding.etWeightUnit.setText(recipe.weightUnit)
        binding.etSourceUrl.setText(recipe.sourceUrl ?: "")
        
        // Images
        imageAdapter.submitList(recipe.images)
        updateImagesVisibility()
        
        // Categories
        selectedCategories = recipe.categoryNames.toMutableSet()
        setupCategoryChips()
        
        // Ingredients
        ingredientAdapter.submitList(recipe.ingredients.map { 
            IngredientRequest(it.name, it.amount) 
        })
        updateIngredientsVisibility()
    }
    
    private fun populateFromImport(data: ImportedRecipeData) {
        binding.etTitle.setText(data.title)
        binding.etInstructions.setText(data.instructions)
        binding.etPrepTime.setText(data.prepTime.toString())
        binding.etRestTime.setText(data.restTime.toString())
        binding.etCookTime.setText(data.cookTime.toString())
        binding.etServings.setText(data.servings.toString())
        binding.etCalories.setText(if (data.caloriesPerUnit > 0) data.caloriesPerUnit.toString() else "")
        binding.etWeightUnit.setText(data.weightUnit)
        binding.etSourceUrl.setText(data.sourceUrl)
        
        // Images
        imageAdapter.submitList(data.images)
        updateImagesVisibility()
        
        // Categories
        selectedCategories = data.categories.toMutableSet()
        setupCategoryChips()
        
        // Ingredients
        ingredientAdapter.submitList(data.ingredients)
        updateIngredientsVisibility()
    }
    
    private fun addIngredient() {
        val name = binding.etIngredientName.text.toString().trim()
        val amount = binding.etIngredientAmount.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.tilIngredientName.error = "Name erforderlich"
            return
        }
        
        if (amount.isEmpty()) {
            binding.tilIngredientAmount.error = "Menge erforderlich"
            return
        }
        
        binding.tilIngredientName.error = null
        binding.tilIngredientAmount.error = null
        
        ingredientAdapter.addIngredient(IngredientRequest(name, amount))
        updateIngredientsVisibility()
        
        // Clear input fields
        binding.etIngredientName.text?.clear()
        binding.etIngredientAmount.text?.clear()
        binding.etIngredientAmount.requestFocus()
    }
    
    private fun updateIngredientsVisibility() {
        val hasIngredients = !ingredientAdapter.isEmpty()
        binding.tvNoIngredients.visibility = if (hasIngredients) View.GONE else View.VISIBLE
        binding.recyclerViewIngredients.visibility = if (hasIngredients) View.VISIBLE else View.GONE
        binding.btnClearIngredients.visibility = if (hasIngredients) View.VISIBLE else View.GONE
    }
    
    private fun saveRecipe() {
        val title = binding.etTitle.text.toString().trim()
        
        if (title.isEmpty()) {
            binding.tilTitle.error = "Titel erforderlich"
            return
        }
        binding.tilTitle.error = null
        
        // Get images from the adapter
        val images = imageAdapter.getImages()
        
        // Debug logging for images
        Log.d(TAG, "Saving recipe with ${images.size} images")
        images.forEachIndexed { index, img ->
            val sizeKb = img.length / 1024
            val isBase64 = img.startsWith("data:")
            Log.d(TAG, "Image $index: ${sizeKb}KB, isBase64=$isBase64, prefix=${img.take(50)}...")
        }
        
        val prepTime = binding.etPrepTime.text.toString().toIntOrNull() ?: 0
        val restTime = binding.etRestTime.text.toString().toIntOrNull() ?: 0
        val cookTime = binding.etCookTime.text.toString().toIntOrNull() ?: 0
        
        val recipe = RecipeRequest(
            title = title,
            images = images,
            instructions = binding.etInstructions.text.toString(),
            prepTime = prepTime,
            restTime = restTime,
            cookTime = cookTime,
            totalTime = prepTime + restTime + cookTime,
            servings = binding.etServings.text.toString().toIntOrNull() ?: 4,
            caloriesPerUnit = binding.etCalories.text.toString().toIntOrNull() ?: 0,
            weightUnit = binding.etWeightUnit.text.toString().trim(),
            sourceUrl = binding.etSourceUrl.text.toString().trim().ifEmpty { null },
            ingredients = ingredientAdapter.getIngredients(),
            categories = selectedCategories.toList()
        )
        
        lifecycleScope.launch {
            setLoading(true)
            
            val result = if (recipeId != null) {
                recipeRepository.updateRecipe(recipeId!!, recipe)
            } else {
                recipeRepository.createRecipe(recipe)
            }
            
            setLoading(false)
            
            result.onSuccess { savedRecipe ->
                Log.d(TAG, "Recipe saved successfully! Returned ${savedRecipe.images.size} images")
                savedRecipe.images.forEachIndexed { index, img ->
                    val sizeKb = img.length / 1024
                    Log.d(TAG, "Saved image $index: ${sizeKb}KB, prefix=${img.take(50)}...")
                }
                Toast.makeText(this@RecipeEditActivity, R.string.recipe_saved, Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Log.e(TAG, "Failed to save recipe", error)
                showError(error.message ?: "Rezept konnte nicht gespeichert werden")
            }
        }
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
        binding.btnCancel.isEnabled = !isLoading
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
