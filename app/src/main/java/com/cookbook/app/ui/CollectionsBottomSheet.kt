package com.cookbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookbook.app.R
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.BottomSheetCollectionsBinding
import com.cookbook.app.ui.adapter.CollectionAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Bottom sheet for selecting a collection to filter recipes
 */
class CollectionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCollectionsBinding? = null
    private val binding get() = _binding!!

    private val recipeRepository by lazy { RecipeRepository() }
    private lateinit var collectionAdapter: CollectionAdapter

    private var selectedCollectionId: String? = null
    private var onCollectionSelected: ((CookbookCollection?) -> Unit)? = null

    companion object {
        private const val ARG_SELECTED_COLLECTION_ID = "selected_collection_id"

        fun newInstance(
            selectedCollectionId: String? = null,
            onCollectionSelected: (CookbookCollection?) -> Unit
        ): CollectionsBottomSheet {
            return CollectionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED_COLLECTION_ID, selectedCollectionId)
                }
                this.onCollectionSelected = onCollectionSelected
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedCollectionId = arguments?.getString(ARG_SELECTED_COLLECTION_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCollectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAllRecipesCard()
        loadCollections()
    }

    private fun setupRecyclerView() {
        collectionAdapter = CollectionAdapter { collection ->
            onCollectionSelected?.invoke(collection)
            dismiss()
        }
        collectionAdapter.setSelectedCollection(selectedCollectionId)

        binding.recyclerViewCollections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = collectionAdapter
        }
    }

    private fun setupAllRecipesCard() {
        // Show checkmark if no collection is selected
        binding.ivAllRecipesCheck.visibility = 
            if (selectedCollectionId == null) View.VISIBLE else View.GONE

        binding.cardAllRecipes.setOnClickListener {
            onCollectionSelected?.invoke(null)
            dismiss()
        }
    }

    private fun loadCollections() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val result = recipeRepository.getCollections()

            binding.progressBar.visibility = View.GONE

            result.onSuccess { collections ->
                if (collections.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.recyclerViewCollections.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.recyclerViewCollections.visibility = View.VISIBLE
                    
                    val items = collections.map { collection ->
                        CollectionAdapter.CollectionItem(
                            collection = collection,
                            firstRecipeImage = collection.firstRecipeImage
                        )
                    }
                    collectionAdapter.submitList(items)
                }
            }.onFailure {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = getString(R.string.error_saving)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
