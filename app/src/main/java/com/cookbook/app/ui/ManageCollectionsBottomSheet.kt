package com.cookbook.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookbook.app.R
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.BottomSheetManageCollectionsBinding
import com.cookbook.app.ui.adapter.CollectionCheckboxAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Bottom sheet for managing which collections a recipe belongs to
 */
class ManageCollectionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetManageCollectionsBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository by lazy { RecipeRepository() }
    private lateinit var collectionAdapter: CollectionCheckboxAdapter

    private var recipe: Recipe? = null
    
    // Callback when collections are updated
    var onCollectionsUpdated: (() -> Unit)? = null

    companion object {
        private const val TAG = "ManageCollectionsSheet"
        
        fun newInstance(recipe: Recipe): ManageCollectionsBottomSheet {
            return ManageCollectionsBottomSheet().apply {
                this.recipe = recipe
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetManageCollectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadCollections()
    }

    private fun setupRecyclerView() {
        collectionAdapter = CollectionCheckboxAdapter { collection, isSelected ->
            toggleCollection(collection, isSelected)
        }
        binding.recyclerViewCollections.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = collectionAdapter
        }
    }

    private fun loadCollections() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            val result = recipeRepository.getCollections()
            
            binding.progressBar.visibility = View.GONE

            result.onSuccess { collections ->
                // Get IDs of collections this recipe belongs to
                val recipeCollectionIds = recipe?.collections?.map { it.id }?.toSet() ?: emptySet()
                
                // Create items with selection state
                val items = collections.map { collection ->
                    CollectionCheckboxAdapter.CollectionItem(
                        collection = collection,
                        isSelected = collection.id in recipeCollectionIds
                    )
                }.sortedBy { it.collection.name }
                
                collectionAdapter.submitList(items)

                if (collections.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                    binding.recyclerViewCollections.visibility = View.GONE
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.recyclerViewCollections.visibility = View.VISIBLE
                }
            }.onFailure { error ->
                Log.e(TAG, "Error loading collections", error)
                Toast.makeText(context, R.string.error_updating_collection, Toast.LENGTH_SHORT).show()
                binding.emptyStateContainer.visibility = View.VISIBLE
                binding.recyclerViewCollections.visibility = View.GONE
            }
        }
    }

    private fun toggleCollection(collection: CookbookCollection, shouldAdd: Boolean) {
        val recipeId = recipe?.id ?: return
        
        lifecycleScope.launch {
            val result = if (shouldAdd) {
                recipeRepository.addRecipeToCollection(collection.id, recipeId)
            } else {
                recipeRepository.removeRecipeFromCollection(collection.id, recipeId)
            }

            result.onSuccess {
                val message = if (shouldAdd) {
                    R.string.recipe_added_to_collection
                } else {
                    R.string.recipe_removed_from_collection
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                
                // Update adapter
                collectionAdapter.updateSelection(collection.id, shouldAdd)
                
                // Notify parent
                onCollectionsUpdated?.invoke()
            }.onFailure { error ->
                Log.e(TAG, "Error toggling collection", error)
                Toast.makeText(context, R.string.error_updating_collection, Toast.LENGTH_SHORT).show()
                
                // Revert checkbox state
                collectionAdapter.updateSelection(collection.id, !shouldAdd)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
