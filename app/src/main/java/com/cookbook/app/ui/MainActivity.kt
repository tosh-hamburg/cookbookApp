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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.FeaturedRecipe
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.data.repository.AuthRepository
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityMainBinding
import com.cookbook.app.ui.adapter.RecipeAdapter
import com.cookbook.app.ui.adapter.RecipeListHeaderAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rezeptliste — stöbern, filtern, sich für heute entscheiden.
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

    /** Ein Filter-Chip steht entweder für eine Sammlung oder für eine Kategorie. */
    private sealed interface FilterChip {
        data object All : FilterChip
        data class Collection(val id: String, val name: String) : FilterChip
        data class Category(val name: String) : FilterChip
    }

    private lateinit var binding: ActivityMainBinding
    private val authRepository by lazy { AuthRepository() }
    private val recipeRepository by lazy { RecipeRepository() }
    private val credentialManager by lazy { CredentialManager.create(this) }
    private lateinit var recipeAdapter: RecipeAdapter
    private lateinit var headerAdapter: RecipeListHeaderAdapter

    // Pagination state
    private var allRecipes: MutableList<RecipeListItem> = mutableListOf()

    /**
     * Ids aller bereits angezeigten Rezepte. Das Backend paginiert die
     * Volltextsuche über `offset`; bei gleichem Relevanzrang ist die
     * Reihenfolge zwischen zwei Abfragen nicht stabil, dadurch rutscht ein
     * Treffer an der Seitengrenze in beide Seiten. Die Web-App merkt davon
     * nichts, weil sie die Liste nicht seitenweise nachlädt.
     */
    private val shownRecipeIds: MutableSet<String> = mutableSetOf()

    private var currentOffset = 0
    private var hasMore = true
    private var isLoadingMore = false
    private var totalRecipes = 0

    // Filter state
    private var categories: List<String> = emptyList()
    private var collections: List<CookbookCollection> = emptyList()
    private var selectedCategory: String? = null
    private var selectedCollections: MutableSet<String> = mutableSetOf()
    private var searchQuery: String = ""
    private var searchJob: Job? = null

    private var featured: FeaturedRecipe? = null

    /** Die Eintritts-Animation gehört zum ersten Öffnen, nicht zu jeder Rückkehr. */
    private var hasAnimatedInitialLoad = false

    /**
     * Zählt die Ladevorgänge. Antworten einer älteren Suche/Filterung dürfen die
     * Liste nicht mehr verändern, sonst mischen sich Treffer verschiedener
     * Suchbegriffe, wenn während des Tippens mehrere Abfragen unterwegs sind.
     */
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        if (!authRepository.isLoggedIn()) {
            navigateToLogin()
            return
        }

        resetAndLoadRecipes()
        loadFeatured()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // Die Wortmarke steckt als eigenes TextView im Toolbar-Slot.
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.textAvatar.text = initialsOfCurrentUser()
        binding.avatarContainer.setOnClickListener { showAccountMenu() }
    }

    /** Initialen für den Avatar-Kreis; ohne Namen bleibt ein neutrales Zeichen. */
    private fun initialsOfCurrentUser(): String {
        val name = authRepository.getCachedUser()?.displayName?.trim().orEmpty()
        if (name.isEmpty()) return "·"
        return name.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }

    private fun showAccountMenu() {
        val name = authRepository.getCachedUser()?.displayName ?: getString(R.string.unknown)
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf(getString(R.string.weekly_planner), getString(R.string.logout))) { _, which ->
                when (which) {
                    0 -> openWeeklyPlanner()
                    1 -> performLogout()
                }
            }
            .show()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(
            onRecipeClick = { recipe -> openRecipeDetail(recipe.id) },
            onFavoriteClick = { recipe -> toggleFavorite(recipe) }
        )
        headerAdapter = RecipeListHeaderAdapter(
            onFeaturedClick = { openRecipeDetail(it.id) },
            onCookNow = { openCookMode(it.id) },
            onSchedule = { scheduleFeatured(it) }
        )

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerViewRecipes.apply {
            this.layoutManager = layoutManager
            adapter = ConcatAdapter(headerAdapter, recipeAdapter)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return

                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoadingMore && hasMore &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5
                    ) {
                        loadMoreRecipes()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            resetAndLoadRecipes()
            loadFeatured()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.secondary)
    }

    private fun setupFab() {
        binding.fabImport.setOnClickListener { showAddRecipeSheet() }
        binding.btnEmptyImport.setOnClickListener { showAddRecipeSheet() }
    }

    private fun showAddRecipeSheet() {
        AddRecipeBottomSheet().show(supportFragmentManager, "addRecipe")
    }

    private fun loadData() {
        loadCollections()
        loadCategories()
        loadFeatured()
        resetAndLoadRecipes()
    }

    private fun loadCollections() {
        lifecycleScope.launch {
            recipeRepository.getCollections().onSuccess { loaded ->
                collections = loaded
                buildFilterChips()
            }
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            recipeRepository.getCategories().onSuccess { loaded ->
                categories = loaded
                buildFilterChips()
            }
        }
    }

    /**
     * Rezept der Woche. Fehlt der Endpunkt oder die Historie, tritt das zuletzt
     * angelegte Rezept an seine Stelle — ohne Häufigkeitssatz.
     */
    private fun loadFeatured() {
        lifecycleScope.launch {
            recipeRepository.getFeaturedRecipe()
                .onSuccess { recipe ->
                    featured = recipe
                    headerAdapter.setFeatured(recipe)
                }
                .onFailure { error ->
                    Log.d(TAG, "Kein Rezept der Woche vom Server: ${error.message}")
                    fallbackFeatured()
                }
        }
    }

    private fun fallbackFeatured() {
        if (featured != null) return
        val newest = allRecipes.firstOrNull() ?: return
        val fallback = FeaturedRecipe(
            id = newest.id,
            title = newest.title,
            thumbnail = newest.thumbnail,
            totalTime = newest.totalTime,
            servings = newest.servings,
            categories = newest.categories,
            collections = newest.collections,
            reason = "newest"
        )
        featured = fallback
        headerAdapter.setFeatured(fallback)
    }

    // ==================== Filter ====================

    /**
     * Erster Chip "Alle" leert die Auswahl. Sammlungen lassen sich mehrfach
     * wählen (das Backend nimmt eine Liste entgegen); bei Kategorien ersetzt
     * eine neue Wahl die alte, weil die Liste nur einen Kategoriefilter kennt.
     */
    private fun buildFilterChips() {
        val group = binding.chipGroupFilters
        group.removeAllViews()

        addFilterChip(FilterChip.All, getString(R.string.filter_all), isActive = !hasActiveFilters())
        collections.forEach { collection ->
            addFilterChip(
                FilterChip.Collection(collection.id, collection.name),
                collection.name,
                isActive = selectedCollections.contains(collection.id)
            )
        }
        categories.forEach { category ->
            addFilterChip(
                FilterChip.Category(category),
                category,
                isActive = selectedCategory == category
            )
        }
    }

    /**
     * Der Chip wird aus dem Layout aufgeblasen, damit der Style auf der View
     * landet und nicht nur auf dem ChipDrawable — sonst zeichnet die View den
     * Text in der Material-Vorgabefarbe und der aktive Chip wird unlesbar.
     */
    private fun addFilterChip(filter: FilterChip, label: String, isActive: Boolean) {
        val group = binding.chipGroupFilters
        val chip = layoutInflater.inflate(R.layout.item_filter_chip, group, false) as Chip
        chip.text = label
        chip.isChecked = isActive
        chip.setOnClickListener { onFilterChipClicked(filter) }
        group.addView(chip)
    }

    private fun onFilterChipClicked(filter: FilterChip) {
        when (filter) {
            FilterChip.All -> {
                selectedCategory = null
                selectedCollections.clear()
            }
            is FilterChip.Collection -> {
                if (!selectedCollections.remove(filter.id)) selectedCollections.add(filter.id)
            }
            is FilterChip.Category -> {
                selectedCategory = if (selectedCategory == filter.name) null else filter.name
            }
        }
        buildFilterChips()
        resetAndLoadRecipes()
    }

    private fun hasActiveFilters(): Boolean =
        selectedCategory != null || selectedCollections.isNotEmpty()

    // ==================== Laden ====================

    private fun resetAndLoadRecipes() {
        currentOffset = 0
        hasMore = true
        isLoadingMore = false
        loadGeneration++
        allRecipes.clear()
        shownRecipeIds.clear()
        recipeAdapter.submitList(emptyList())
        loadRecipes(isInitialLoad = true)
    }

    private fun loadMoreRecipes() {
        if (isLoadingMore || !hasMore) return
        loadRecipes(isInitialLoad = false)
    }

    private fun loadRecipes(isInitialLoad: Boolean) {
        val generation = loadGeneration

        lifecycleScope.launch {
            if (isInitialLoad) setLoading(true) else isLoadingMore = true

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

            result.onSuccess { page ->
                totalRecipes = page.total
                hasMore = page.hasMore
                // Der Offset zählt über die Antwort des Servers, nicht über die
                // angezeigte Liste — sonst würde eine verworfene Dublette die
                // nächste Seite verschieben.
                currentOffset += page.items.size

                val newItems = page.items.filter { shownRecipeIds.add(it.id) }

                if (isInitialLoad) {
                    allRecipes = newItems.toMutableList()
                    if (!hasAnimatedInitialLoad) {
                        hasAnimatedInitialLoad = true
                        recipeAdapter.playEnterAnimation(newItems.size)
                    }
                } else {
                    allRecipes.addAll(newItems)
                }
                recipeAdapter.submitList(allRecipes.toList())
                headerAdapter.setRecipeCount(totalRecipes)

                if (featured == null) fallbackFeatured()
                updateRecipeList()

                // Bestand eine Seite nur aus Dubletten, wächst die Liste nicht
                // und das Ende des Scrollbereichs löst kein weiteres Nachladen
                // aus. Dann wird direkt weitergeblättert.
                if (!isInitialLoad && newItems.isEmpty() && page.items.isNotEmpty() && hasMore) {
                    loadMoreRecipes()
                }
            }.onFailure { error ->
                Log.e(TAG, "loadRecipes onFailure", error)
                showError(error.message ?: "Fehler beim Laden der Rezepte")
            }
        }
    }

    private fun updateRecipeList() {
        val isEmpty = allRecipes.isEmpty()
        if (isEmpty) updateEmptyStateText()
        binding.emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewRecipes.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * "Bereit für das erste Rezept" stimmt nur ohne Suche und Filter. Sucht der
     * Nutzer, liegt es am Suchbegriff — dann muss der leere Zustand das sagen.
     */
    private fun updateEmptyStateText() {
        val isFiltered = searchQuery.isNotEmpty() || hasActiveFilters()

        if (isFiltered) {
            binding.textEmptyStateTitle.setText(R.string.no_recipes_found)
            binding.textEmptyStateHint.setText(R.string.adjust_search_or_filters)
            binding.btnEmptyImport.visibility = View.GONE
        } else {
            binding.textEmptyStateTitle.setText(R.string.empty_first_recipe_title)
            binding.textEmptyStateHint.setText(R.string.empty_first_recipe_hint)
            binding.btnEmptyImport.visibility = View.VISIBLE
        }
    }

    // ==================== Favoriten ====================

    /**
     * Das Herz schaltet optimistisch — der Adapter hat die Anzeige schon
     * umgestellt. Schlägt der Aufruf fehl, wird der alte Stand wiederhergestellt.
     */
    private fun toggleFavorite(recipe: RecipeListItem) {
        val target = !recipe.isFavorite
        applyFavoriteLocally(recipe.id, target)

        lifecycleScope.launch {
            recipeRepository.setFavorite(recipe.id, target)
                .onSuccess { response -> applyFavoriteLocally(recipe.id, response.isFavorite) }
                .onFailure {
                    applyFavoriteLocally(recipe.id, recipe.isFavorite)
                    showError(getString(R.string.favorite_failed))
                }
        }
    }

    private fun applyFavoriteLocally(recipeId: String, isFavorite: Boolean) {
        val index = allRecipes.indexOfFirst { it.id == recipeId }
        if (index < 0) return
        allRecipes[index] = allRecipes[index].copy(isFavorite = isFavorite)
        recipeAdapter.submitList(allRecipes.toList())
    }

    // ==================== Navigation ====================

    private fun openRecipeDetail(recipeId: String) {
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        startActivity(intent)
    }

    private fun openCookMode(recipeId: String) {
        val intent = Intent(this, CookModeActivity::class.java)
        intent.putExtra(CookModeActivity.EXTRA_RECIPE_ID, recipeId)
        startActivity(intent)
    }

    private fun scheduleFeatured(recipe: FeaturedRecipe) {
        lifecycleScope.launch {
            recipeRepository.getRecipe(recipe.id)
                .onSuccess { full ->
                    AddToWeekPlannerBottomSheet.newInstance(full)
                        .show(supportFragmentManager, "AddToWeekPlanner")
                }
                .onFailure { showError(getString(R.string.error_adding_to_planner)) }
        }
    }

    /** Sammlungen verwalten und direkt als Filter übernehmen. */
    private fun showCollectionsSheet() {
        CollectionsBottomSheet.newInstance(selectedCollections.firstOrNull()) { collection ->
            selectedCollections.clear()
            collection?.id?.let { selectedCollections.add(it) }
            buildFilterChips()
            resetAndLoadRecipes()
        }.show(supportFragmentManager, "collections")
    }

    private fun openWeeklyPlanner() {
        startActivity(Intent(this, WeeklyPlannerActivity::class.java))
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ==================== Menü ====================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = getString(R.string.what_today)
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
                performLogout(); true
            }
            R.id.action_refresh -> {
                loadData(); true
            }
            R.id.action_collections -> {
                showCollectionsSheet(); true
            }
            R.id.action_weekly_planner -> {
                openWeeklyPlanner(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    authRepository.logout()

                    // Google-Anmeldestatus löschen, damit eine neue Anmeldung möglich ist
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        Log.d(TAG, "Google credential state cleared on logout")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clear credential state: ${e.message}")
                    }

                    navigateToLogin()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
