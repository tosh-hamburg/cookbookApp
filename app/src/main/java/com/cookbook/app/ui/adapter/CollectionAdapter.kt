package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.databinding.ItemCollectionBinding
import com.cookbook.app.util.ImageUtils

/**
 * Adapter for collection list in bottom sheet
 */
class CollectionAdapter(
    private val onCollectionClick: (CookbookCollection) -> Unit
) : ListAdapter<CollectionAdapter.CollectionItem, CollectionAdapter.CollectionViewHolder>(CollectionDiffCallback()) {

    private var selectedCollectionId: String? = null

    fun setSelectedCollection(collectionId: String?) {
        selectedCollectionId = collectionId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder {
        val binding = ItemCollectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CollectionViewHolder(binding, onCollectionClick)
    }

    override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, selectedCollectionId == item.collection.id)
    }

    /**
     * Wrapper class to include first recipe image
     */
    data class CollectionItem(
        val collection: CookbookCollection,
        val firstRecipeImage: String? = null
    )

    class CollectionViewHolder(
        private val binding: ItemCollectionBinding,
        private val onCollectionClick: (CookbookCollection) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CollectionItem, isSelected: Boolean) {
            val collection = item.collection
            
            binding.tvCollectionName.text = collection.name
            binding.tvRecipeCount.text = binding.root.context.getString(
                R.string.recipes_count, 
                collection.recipeCount
            )

            // Show checkmark if selected
            binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Load first recipe image if available
            val imageUrl = item.firstRecipeImage
            if (imageUrl != null) {
                if (ImageUtils.isBase64DataUrl(imageUrl)) {
                    val bitmap = ImageUtils.decodeBase64Image(imageUrl)
                    if (bitmap != null) {
                        val roundedDrawable = RoundedBitmapDrawableFactory.create(
                            binding.root.context.resources,
                            bitmap
                        ).apply {
                            cornerRadius = 8f
                        }
                        binding.ivCollectionImage.setImageDrawable(roundedDrawable)
                    } else {
                        binding.ivCollectionImage.setImageResource(R.drawable.placeholder_recipe)
                    }
                } else {
                    // For regular URLs, we'd use Coil, but keeping it simple
                    binding.ivCollectionImage.setImageResource(R.drawable.placeholder_recipe)
                }
            } else {
                binding.ivCollectionImage.setImageResource(R.drawable.placeholder_recipe)
            }

            binding.root.setOnClickListener {
                onCollectionClick(collection)
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
