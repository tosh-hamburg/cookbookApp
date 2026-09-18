package com.cookbook.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.models.MealType
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.DialogRecipeSearchBinding
import com.cookbook.app.ui.adapter.RecipeSearchAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet dialog for searching and selecting recipes
 */
class RecipeSearchBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogRecipeSearchBinding? = null
    private val binding get() = _binding!!

    private val recipeRepository by lazy { RecipeRepository() }
    private lateinit var adapter: RecipeSearchAdapter

    private var dayIndex: Int = 0
    private var mealType: MealType = MealType.BREAKFAST
    private var dayName: String = ""
    private var onRecipeSelected: ((RecipeListItem, Int, MealType) -> Unit)? = null

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var allRecipes: MutableList<RecipeListItem> = mutableListOf()

    // Pagination state
    private var currentOffset = 0
    private var hasMore = true
    private var isLoadingMore = false
    private var currentSearchQuery = ""

    companion object {
        private const val ARG_DAY_INDEX = "dayIndex"
        private const val ARG_MEAL_TYPE = "mealType"
        private const val ARG_DAY_NAME = "dayName"
        private const val PAGE_SIZE = 30

        /** Wartezeit nach dem letzten Tastendruck, siehe MainActivity. */
        private const val SEARCH_DEBOUNCE_MS = 300L

        fun newInstance(
            dayIndex: Int,
            mealType: MealType,
            dayName: String,
            onRecipeSelected: (RecipeListItem, Int, MealType) -> Unit
        ): RecipeSearchBottomSheet {
            return RecipeSearchBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DAY_INDEX, dayIndex)
                    putString(ARG_MEAL_TYPE, mealType.key)
                    putString(ARG_DAY_NAME, dayName)
                }
                this.onRecipeSelected = onRecipeSelected
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dayIndex = it.getInt(ARG_DAY_INDEX)
            mealType = MealType.fromKey(it.getString(ARG_MEAL_TYPE) ?: "") ?: MealType.BREAKFAST
            dayName = it.getString(ARG_DAY_NAME) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRecipeSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBottomSheetBehavior()
        setupUI()
        setupRecyclerView()
        setupSearch()
        loadRecipes()
    }

    private fun setupBottomSheetBehavior() {
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = resources.displayMetrics.heightPixels * 3 / 4
            behavior.skipCollapsed = true
        }
    }

    private fun setupUI() {
        binding.textSlotInfo.text = "$dayName - ${mealType.getLabel(requireContext())}"

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        adapter = RecipeSearchAdapter { recipe ->
            onRecipeSelected?.invoke(recipe, dayIndex, mealType)
            dismiss()
        }

        val linearLayoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRecipes.apply {
            layoutManager = linearLayoutManager
            adapter = this@RecipeSearchBottomSheet.adapter
            
            // Infinite scroll listener
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    // Auch Suchtreffer werden seitenweise nachgeladen — gefiltert
                    // wird im Backend, nicht in der bereits geladenen Liste.
                    if (dy > 0) {
                        val visibleItemCount = linearLayoutManager.childCount
                        val totalItemCount = linearLayoutManager.itemCount
                        val firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition()
                        
                        // Load more when near the end
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

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    if (query != currentSearchQuery) {
                        loadRecipes(query)
                    }
                }
            }
        })

        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchJob?.cancel()
                loadRecipes(binding.editSearch.text?.toString()?.trim() ?: "")
                true
            } else {
                false
            }
        }
    }

    /**
     * Lädt die erste Seite für [query] neu. Gesucht wird im Backend (Volltext
     * über Titel, Kategorien, Zutaten, Notizen und Zubereitung), damit auch
     * Rezepte gefunden werden, die noch nicht nachgeladen wurden.
     */
    private fun loadRecipes(query: String = "") {
        currentSearchQuery = query

        // Reset pagination state
        currentOffset = 0
        hasMore = true
        isLoadingMore = false
        allRecipes.clear()
        adapter.submitList(emptyList())

        setLoading(true)

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getRecipes(
                search = query.ifEmpty { null },
                limit = PAGE_SIZE,
                offset = 0
            )

            setLoading(false)

            result.onSuccess { paginatedResult ->
                allRecipes = paginatedResult.items.toMutableList()
                hasMore = paginatedResult.hasMore
                currentOffset = paginatedResult.items.size
                adapter.submitList(allRecipes.toList())
                updateEmptyState(allRecipes.isEmpty())
            }.onFailure {
                updateEmptyState(true)
            }
        }
    }

    private fun loadMoreRecipes() {
        if (isLoadingMore || !hasMore) return

        isLoadingMore = true
        val query = currentSearchQuery

        viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getRecipes(
                search = query.ifEmpty { null },
                limit = PAGE_SIZE,
                offset = currentOffset
            )

            // Der Suchbegriff hat sich geändert, während die Seite unterwegs war —
            // sie gehört nicht mehr zur angezeigten Trefferliste.
            if (query != currentSearchQuery) return@launch

            result.onSuccess { paginatedResult ->
                allRecipes.addAll(paginatedResult.items)
                hasMore = paginatedResult.hasMore
                currentOffset += paginatedResult.items.size
                adapter.submitList(allRecipes.toList())
            }

            isLoadingMore = false
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            // Liste und Leerzustand ausblenden; welcher von beiden danach sichtbar
            // wird, entscheidet updateEmptyState anhand des Ergebnisses.
            binding.recyclerViewRecipes.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewRecipes.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob = null
        loadJob = null
        _binding = null
    }
}
