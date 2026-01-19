package com.cookbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.data.repository.AuthRepository
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityMainBinding
import com.cookbook.app.ui.adapter.RecipeAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * Main activity showing recipe list with pagination
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PAGE_SIZE = 20
    }
    
    private lateinit var binding: ActivityMainBinding
    private val authRepository by lazy { AuthRepository() }
    private val recipeRepository by lazy { RecipeRepository() }
    private lateinit var recipeAdapter: RecipeAdapter
    
    // Pagination state
    private var allRecipes: MutableList<RecipeListItem> = mutableListOf()
    private var currentOffset = 0
    private var hasMore = true
    private var isLoadingMore = false
    private var totalRecipes = 0
    
    // Filter state
    private var categories: List<String> = emptyList()
    private var collections: List<CookbookCollection> = emptyList()
    private var selectedCategory: String? = null
    private var selectedCollection: CookbookCollection? = null
    private var searchQuery: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is logged in, redirect to login if not
        if (!authRepository.isLoggedIn()) {
            navigateToLogin()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        
        loadData()
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if still logged in
        if (!authRepository.isLoggedIn()) {
            navigateToLogin()
            return
        }
        
        // Refresh data when returning from other activities
        resetAndLoadRecipes()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        updateToolbarSubtitle()
    }
    
    private fun updateToolbarSubtitle() {
        val user = authRepository.getCachedUser()
        val networkInfo = if (ApiClient.isUsingInternalNetwork()) "intern" else "extern"
        val collectionInfo = selectedCollection?.let { " • 📁 ${it.name}" } ?: ""
        supportActionBar?.subtitle = "${user?.displayName ?: "Unbekannt"} • $networkInfo$collectionInfo"
    }
    
    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter { recipe ->
            openRecipeDetail(recipe)
        }
        
        val layoutManager = GridLayoutManager(this, 2)
        binding.recyclerViewRecipes.apply {
            this.layoutManager = layoutManager
            adapter = recipeAdapter
            
            // Infinite scroll listener
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    if (dy > 0) { // Scrolling down
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                        
                        // Load more when we're near the end (5 items before the end)
                        if (!isLoadingMore && hasMore) {
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                                loadMoreRecipes()
                            }
                        }
                    }
                }
            })
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            resetAndLoadRecipes()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.secondary
        )
    }
    
    private fun setupFab() {
        binding.fabImport.setOnClickListener {
            showImportOptions()
        }
    }
    
    private fun showImportOptions() {
        val options = arrayOf("Rezept importieren (URL)", "Neues Rezept erstellen")
        
        AlertDialog.Builder(this)
            .setTitle("Rezept hinzufügen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openImportActivity()
                    1 -> openCreateRecipe()
                }
            }
            .show()
    }
    
    private fun loadData() {
        loadCollections()
        loadCategories()
        resetAndLoadRecipes()
    }
    
    private fun loadCollections() {
        lifecycleScope.launch {
            val result = recipeRepository.getCollections()
            result.onSuccess { loadedCollections ->
                collections = loadedCollections
                setupCollectionChips()
            }
        }
    }
    
    private fun loadCategories() {
        lifecycleScope.launch {
            val result = recipeRepository.getCategories()
            result.onSuccess { loadedCategories ->
                categories = loadedCategories
                setupCategoryChips()
            }
        }
    }
    
    /**
     * Reset pagination and load first page
     */
    private fun resetAndLoadRecipes() {
        currentOffset = 0
        hasMore = true
        allRecipes.clear()
        recipeAdapter.submitList(emptyList())
        loadRecipes(isInitialLoad = true)
    }
    
    /**
     * Load more recipes (next page)
     */
    private fun loadMoreRecipes() {
        if (isLoadingMore || !hasMore) return
        loadRecipes(isInitialLoad = false)
    }
    
    /**
     * Load recipes with pagination
     */
    private fun loadRecipes(isInitialLoad: Boolean) {
        lifecycleScope.launch {
            Log.d(TAG, "loadRecipes: isInitialLoad=$isInitialLoad, offset=$currentOffset")
            
            if (isInitialLoad) {
                setLoading(true)
            } else {
                isLoadingMore = true
            }
            
            val result = recipeRepository.getRecipes(
                category = selectedCategory,
                collection = selectedCollection?.id,
                search = searchQuery.ifEmpty { null },
                limit = PAGE_SIZE,
                offset = currentOffset
            )
            
            if (isInitialLoad) {
                setLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
            } else {
                isLoadingMore = false
            }
            
            result.onSuccess { paginatedResult ->
                Log.d(TAG, "loadRecipes success: ${paginatedResult.items.size} items, total=${paginatedResult.total}, hasMore=${paginatedResult.hasMore}")
                
                totalRecipes = paginatedResult.total
                hasMore = paginatedResult.hasMore
                currentOffset += paginatedResult.items.size
                
                if (isInitialLoad) {
                    allRecipes = paginatedResult.items.toMutableList()
                    recipeAdapter.submitList(allRecipes.toList())
                } else {
                    allRecipes.addAll(paginatedResult.items)
                    recipeAdapter.submitList(allRecipes.toList())
                }
                
                updateRecipeList()
            }.onFailure { error ->
                Log.e(TAG, "loadRecipes onFailure", error)
                showError(error.message ?: "Fehler beim Laden der Rezepte")
            }
        }
    }
    
    private fun setupCollectionChips() {
        binding.chipGroupCollections.removeAllViews()
        
        if (collections.isEmpty()) {
            binding.scrollViewCollections.visibility = View.GONE
            return
        }
        
        binding.scrollViewCollections.visibility = View.VISIBLE
        
        // "Alle" chip for collections
        val allChip = Chip(this).apply {
            text = getString(R.string.all_recipes)
            isCheckable = true
            isChecked = selectedCollection == null
            setOnClickListener {
                selectedCollection = null
                updateCollectionChips()
                updateToolbarSubtitle()
                resetAndLoadRecipes()
            }
        }
        binding.chipGroupCollections.addView(allChip)
        
        // Collection chips
        collections.forEach { collection ->
            val chip = Chip(this).apply {
                text = "${collection.name} (${collection.recipeCount})"
                isCheckable = true
                isChecked = selectedCollection?.id == collection.id
                setOnClickListener {
                    selectedCollection = collection
                    updateCollectionChips()
                    updateToolbarSubtitle()
                    resetAndLoadRecipes()
                }
            }
            binding.chipGroupCollections.addView(chip)
        }
    }
    
    private fun updateCollectionChips() {
        for (i in 0 until binding.chipGroupCollections.childCount) {
            val chip = binding.chipGroupCollections.getChildAt(i) as? Chip
            chip?.isChecked = when {
                i == 0 -> selectedCollection == null
                else -> {
                    val collectionName = collections.getOrNull(i - 1)?.name
                    chip?.text?.startsWith(collectionName ?: "") == true && selectedCollection?.name == collectionName
                }
            }
        }
    }
    
    private fun setupCategoryChips() {
        binding.chipGroupCategories.removeAllViews()
        
        // "Alle" chip
        val allChip = Chip(this).apply {
            text = "Alle"
            isCheckable = true
            isChecked = selectedCategory == null
            setOnClickListener {
                selectedCategory = null
                updateCategoryChips()
                resetAndLoadRecipes()
            }
        }
        binding.chipGroupCategories.addView(allChip)
        
        // Category chips
        categories.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = selectedCategory == category
                setOnClickListener {
                    selectedCategory = category
                    updateCategoryChips()
                    resetAndLoadRecipes()
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }
    
    private fun updateCategoryChips() {
        for (i in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(i) as? Chip
            chip?.isChecked = when {
                i == 0 -> selectedCategory == null
                else -> chip?.text == selectedCategory
            }
        }
    }
    
    private fun updateRecipeList() {
        Log.d(TAG, "updateRecipeList: ${allRecipes.size} recipes, total=$totalRecipes")
        if (allRecipes.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerViewRecipes.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerViewRecipes.visibility = View.VISIBLE
        }
    }
    
    private fun openRecipeDetail(recipe: RecipeListItem) {
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
        startActivity(intent)
    }
    
    private fun openImportActivity() {
        val intent = Intent(this, RecipeImportActivity::class.java)
        startActivity(intent)
    }
    
    private fun openCreateRecipe() {
        val intent = Intent(this, RecipeEditActivity::class.java)
        startActivity(intent)
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // ==================== Menu ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        
        // Setup search
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        
        searchView.queryHint = getString(R.string.search_recipes)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query ?: ""
                resetAndLoadRecipes()
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                if (searchQuery.isEmpty()) {
                    resetAndLoadRecipes()
                }
                return true
            }
        })
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                performLogout()
                true
            }
            R.id.action_refresh -> {
                loadData()
                true
            }
            R.id.action_weekly_planner -> {
                openWeeklyPlanner()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openWeeklyPlanner() {
        val intent = Intent(this, WeeklyPlannerActivity::class.java)
        startActivity(intent)
    }
    
    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("Abmelden")
            .setMessage("Möchtest du dich wirklich abmelden?")
            .setPositiveButton("Ja") { _, _ ->
                lifecycleScope.launch {
                    authRepository.logout()
                    navigateToLogin()
                }
            }
            .setNegativeButton("Nein", null)
            .show()
    }
}
