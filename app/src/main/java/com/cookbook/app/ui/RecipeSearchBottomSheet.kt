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
    private var allRecipes: List<RecipeListItem> = emptyList()

    companion object {
        private const val ARG_DAY_INDEX = "dayIndex"
        private const val ARG_MEAL_TYPE = "mealType"
        private const val ARG_DAY_NAME = "dayName"

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
        binding.textSlotInfo.text = "$dayName - ${mealType.label}"

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        adapter = RecipeSearchAdapter { recipe ->
            onRecipeSelected?.invoke(recipe, dayIndex, mealType)
            dismiss()
        }

        binding.recyclerViewRecipes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecipeSearchBottomSheet.adapter
        }
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    filterRecipes(s?.toString() ?: "")
                }
            }
        })

        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterRecipes(binding.editSearch.text?.toString() ?: "")
                true
            } else {
                false
            }
        }
    }

    private fun loadRecipes() {
        setLoading(true)

        lifecycleScope.launch {
            val result = recipeRepository.getRecipes(limit = 100, offset = 0)

            result.onSuccess { paginatedResult ->
                allRecipes = paginatedResult.items
                adapter.submitList(allRecipes)
                updateEmptyState(allRecipes.isEmpty())
            }.onFailure {
                updateEmptyState(true)
            }

            setLoading(false)
        }
    }

    private fun filterRecipes(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allRecipes)
            updateEmptyState(allRecipes.isEmpty())
            return
        }

        val filteredList = allRecipes.filter { recipe ->
            recipe.title.contains(query, ignoreCase = true) ||
                    recipe.categories.any { it.contains(query, ignoreCase = true) }
        }

        adapter.submitList(filteredList)
        updateEmptyState(filteredList.isEmpty())
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.recyclerViewRecipes.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewRecipes.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
