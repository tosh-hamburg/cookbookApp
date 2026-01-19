package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.databinding.ItemCollectionCheckboxBinding

/**
 * Adapter for displaying collections with checkboxes
 */
class CollectionCheckboxAdapter(
    private val onCollectionToggle: (CookbookCollection, Boolean) -> Unit
) : ListAdapter<CollectionCheckboxAdapter.CollectionItem, CollectionCheckboxAdapter.CollectionViewHolder>(CollectionDiffCallback()) {

    data class CollectionItem(
        val collection: CookbookCollection,
        var isSelected: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder {
        val binding = ItemCollectionCheckboxBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CollectionViewHolder(binding, onCollectionToggle)
    }

    override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateSelection(collectionId: String, isSelected: Boolean) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.collection.id == collectionId }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(isSelected = isSelected)
            submitList(currentList)
        }
    }

    class CollectionViewHolder(
        private val binding: ItemCollectionCheckboxBinding,
        private val onCollectionToggle: (CookbookCollection, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CollectionItem) {
            binding.tvCollectionName.text = item.collection.name
            binding.tvRecipeCount.text = binding.root.context.resources.getQuantityString(
                R.plurals.recipe_count,
                item.collection.recipeCount,
                item.collection.recipeCount
            )
            
            binding.checkbox.isChecked = item.isSelected
            
            binding.root.setOnClickListener {
                val newState = !binding.checkbox.isChecked
                binding.checkbox.isChecked = newState
                onCollectionToggle(item.collection, newState)
            }
            
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != item.isSelected) {
                    onCollectionToggle(item.collection, isChecked)
                }
            }
        }
    }

    class CollectionDiffCallback : DiffUtil.ItemCallback<CollectionItem>() {
        override fun areItemsTheSame(oldItem: CollectionItem, newItem: CollectionItem): Boolean {
            return oldItem.collection.id == newItem.collection.id
        }

        override fun areContentsTheSame(oldItem: CollectionItem, newItem: CollectionItem): Boolean {
            return oldItem == newItem
        }
    }
}
