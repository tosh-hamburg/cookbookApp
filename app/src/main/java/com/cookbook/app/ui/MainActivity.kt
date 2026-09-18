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
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main activity showing recipe list with pagination
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PAGE_SIZE = 20

        /**
         * Wartezeit nach dem letzten Tastendruck, bevor gesucht wird. Die
         * Volltextsuche läuft im Backend — ohne Entprellung löst jeder Buchstabe
         * eine eigene Abfrage aus.
         */
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
    
    private lateinit var binding: ActivityMainBinding
    private val authRepository by lazy { AuthRepository() }
    private val recipeRepository by lazy { RecipeRepository() }
    private val credentialManager by lazy { CredentialManager.create(this) }
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
    private var selectedCollections: MutableSet<String> = mutableSetOf() // Set of collection IDs
    private var searchQuery: String = ""
    private var searchJob: Job? = null

    /**
     * Zählt die Ladevorgänge. Antworten einer älteren Suche/Filterung dürfen die
     * Liste nicht mehr verändern, sonst mischen sich Treffer verschiedener
     * Suchbegriffe, wenn während des Tippens mehrere Abfragen unterwegs sind.
     */
    private var loadGeneration = 0

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
        setupFilterButtons()
        
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
        val collectionInfo = if (selectedCollections.isNotEmpty()) {
            val selectedNames = collections
                .filter { selectedCollections.contains(it.id) }
                .map { it.name }
            " • 📁 ${selectedNames.joinToString(", ")}"
        } else ""
        supportActionBar?.subtitle = "${user?.displayName ?: getString(R.string.unknown)}$collectionInfo"
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
                updateFilterButtonLabels()
            }
        }
    }
    
    private fun loadCategories() {
        lifecycleScope.launch {
            val result = recipeRepository.getCategories()
            result.onSuccess { loadedCategories ->
                categories = loadedCategories
                updateFilterButtonLabels()
            }
        }
    }
    
    private fun setupFilterButtons() {
        // Category filter button
        binding.btnFilterCategory.setOnClickListener {
            showCategoryFilterDialog()
        }
        
        // Collections filter button
        binding.btnFilterCollections.setOnClickListener {
            showCollectionsFilterDialog()
        }
        
        // Clear filters button
        binding.btnClearFilters.setOnClickListener {
            clearAllFilters()
        }
        
        updateFilterButtonLabels()
    }
    
    private fun showCategoryFilterDialog() {
        // Create items for single-select category filter
        val items = categories.map { category ->
            FilterSelectionBottomSheet.FilterItem(
                id = category,
                name = category
            )
        }
        
        val selectedIds = if (selectedCategory != null) setOf(selectedCategory!!) else emptySet()
        
        val bottomSheet = FilterSelectionBottomSheet.newInstance(
            title = getString(R.string.select_categories),
            items = items,
            selectedIds = selectedIds,
            emptyIcon = "🏷️",
            emptyMessage = getString(R.string.no_categories),
            showCounts = false
        )
        
        bottomSheet.onSelectionApplied = { newSelection ->
            // Single selection for category - take first or null
            selectedCategory = newSelection.firstOrNull()
            updateFilterButtonLabels()
            updateToolbarSubtitle()
            resetAndLoadRecipes()
        }
        
        bottomSheet.show(supportFragmentManager, "categoryFilter")
    }
    
    private fun showCollectionsFilterDialog() {
        // Create items for multi-select collections filter
        val items = collections.map { collection ->
            FilterSelectionBottomSheet.FilterItem(
                id = collection.id,
                name = collection.name,
                count = collection.recipeCount
            )
        }
        
        val bottomSheet = FilterSelectionBottomSheet.newInstance(
            title = getString(R.string.select_collections),
            items = items,
            selectedIds = selectedCollections,
            emptyIcon = "📚",
            emptyMessage = getString(R.string.no_collections),
            showCounts = true
        )
        
        bottomSheet.onSelectionApplied = { newSelection ->
            selectedCollections.clear()
            selectedCollections.addAll(newSelection)
            updateFilterButtonLabels()
            updateToolbarSubtitle()
            resetAndLoadRecipes()
        }
        
        bottomSheet.show(supportFragmentManager, "collectionsFilter")
    }
    
    private fun clearAllFilters() {
        selectedCategory = null
        selectedCollections.clear()
        updateFilterButtonLabels()
        updateToolbarSubtitle()
        resetAndLoadRecipes()
    }
    
    private fun updateFilterButtonLabels() {
        // Update category button
        binding.btnFilterCategory.text = selectedCategory ?: getString(R.string.all_categories)
        
        // Update collections button
        val collectionsText = when {
            selectedCollections.isEmpty() -> getString(R.string.collections)
            selectedCollections.size == 1 -> {
                collections.find { it.id == selectedCollections.first() }?.name ?: getString(R.string.collections)
            }
            else -> getString(R.string.selected_count, selectedCollections.size)
        }
        binding.btnFilterCollections.text = collectionsText
        
        // Show/hide clear button based on active filters
        val hasActiveFilters = selectedCategory != null || selectedCollections.isNotEmpty()
        binding.btnClearFilters.visibility = if (hasActiveFilters) View.VISIBLE else View.GONE
        
        // Highlight active filter buttons
        val activeColor = getColor(R.color.primary)
        val inactiveColor = getColor(R.color.text_secondary)
        
        binding.btnFilterCategory.setTextColor(if (selectedCategory != null) activeColor else inactiveColor)
        binding.btnFilterCollections.setTextColor(if (selectedCollections.isNotEmpty()) activeColor else inactiveColor)
    }
    
    /**
     * Reset pagination and load first page
     */
    private fun resetAndLoadRecipes() {
        currentOffset = 0
        hasMore = true
        isLoadingMore = false
        loadGeneration++
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
        val generation = loadGeneration

        lifecycleScope.launch {
            Log.d(TAG, "loadRecipes: isInitialLoad=$isInitialLoad, offset=$currentOffset, search=$searchQuery")

            if (isInitialLoad) {
                setLoading(true)
            } else {
                isLoadingMore = true
            }

            val result = recipeRepository.getRecipes(
                category = selectedCategory,
                collectionIds = selectedCollections.toList().ifEmpty { null },
                search = searchQuery.ifEmpty { null },
                limit = PAGE_SIZE,
                offset = currentOffset
            )

            // Suchbegriff oder Filter haben sich geändert, während die Antwort
            // unterwegs war — dieses Ergebnis gehört nicht mehr zur Anzeige.
            if (generation != loadGeneration) {
                Log.d(TAG, "loadRecipes: Ergebnis einer überholten Abfrage verworfen")
                return@launch
            }

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
    
    private fun updateRecipeList() {
        Log.d(TAG, "updateRecipeList: ${allRecipes.size} recipes, total=$totalRecipes")
        if (allRecipes.isEmpty()) {
            updateEmptyStateText()
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerViewRecipes.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerViewRecipes.visibility = View.VISIBLE
        }
    }

    /**
     * "Noch keine Rezepte vorhanden" stimmt nur ohne Suche und Filter. Sucht der
     * Nutzer, liegt es am Suchbegriff — dann muss der leere Zustand das sagen.
     */
    private fun updateEmptyStateText() {
        val isFiltered = searchQuery.isNotEmpty() ||
                selectedCategory != null ||
                selectedCollections.isNotEmpty()

        if (isFiltered) {
            binding.textEmptyStateIcon.text = getString(R.string.empty_state_icon_search)
            binding.textEmptyStateTitle.setText(R.string.no_recipes_found)
            binding.textEmptyStateHint.setText(R.string.adjust_search_or_filters)
        } else {
            binding.textEmptyStateIcon.text = getString(R.string.empty_state_icon_cooking)
            binding.textEmptyStateTitle.setText(R.string.no_recipes)
            binding.textEmptyStateHint.setText(R.string.add_first_recipe)
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
                searchJob?.cancel()
                applySearchQuery(query ?: "")
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    applySearchQuery(query)
                }
                return true
            }
        })

        // Das Einklappen des Suchfelds leert zwar die Eingabe, die zuletzt
        // gesuchte Liste bliebe aber stehen.
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchJob?.cancel()
                applySearchQuery("")
                return true
            }
        })

        return true
    }

    /**
     * Übernimmt einen Suchbegriff und lädt die Liste neu. Die eigentliche Suche
     * läuft im Backend (Volltext über Titel, Kategorien, Zutaten, Notizen und
     * Zubereitung), damit auch noch nicht geladene Rezepte gefunden werden.
     */
    private fun applySearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed == searchQuery) return

        searchQuery = trimmed
        resetAndLoadRecipes()
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
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    // Clear app authentication state
                    authRepository.logout()
                    
                    // Clear Google Sign-In credential state to allow fresh sign-in
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        Log.d(TAG, "Google credential state cleared on logout")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clear credential state: ${e.message}")
                        // Continue anyway, not critical for logout
                    }
                    
                    navigateToLogin()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
