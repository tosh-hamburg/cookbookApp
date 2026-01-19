package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.cookbook.app.R
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.databinding.ItemRecipeBinding
import com.cookbook.app.util.ImageUtils

/**
 * Adapter for recipe list (uses RecipeListItem with thumbnails for better performance)
 */
class RecipeAdapter(
    private val onRecipeClick: (RecipeListItem) -> Unit
) : ListAdapter<RecipeListItem, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding, onRecipeClick)
    }
    
    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    /**
     * Add more items to the existing list (for pagination)
     */
    fun addItems(newItems: List<RecipeListItem>) {
        val currentList = currentList.toMutableList()
        currentList.addAll(newItems)
        submitList(currentList)
    }
    
    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val onRecipeClick: (RecipeListItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(recipe: RecipeListItem) {
            binding.tvTitle.text = recipe.title
            
            // Time info
            binding.tvTime.text = "${recipe.totalTime} Min."
            
            // Categories as subtitle
            val categories = recipe.categories
            binding.tvCategories.text = if (categories.isNotEmpty()) {
                categories.joinToString(" • ")
            } else {
                ""
            }
            
            // Image (thumbnail from backend)
            val thumbnail = recipe.thumbnail
            if (thumbnail != null) {
                if (ImageUtils.isBase64DataUrl(thumbnail)) {
                    // Handle base64 data URL (thumbnail)
                    val bitmap = ImageUtils.decodeBase64Image(thumbnail)
                    if (bitmap != null) {
                        // Create rounded corners drawable
                        val roundedDrawable = RoundedBitmapDrawableFactory.create(
                            binding.root.context.resources,
                            bitmap
                        ).apply {
                            cornerRadius = 16f
                        }
                        binding.ivRecipeImage.setImageDrawable(roundedDrawable)
                    } else {
                        binding.ivRecipeImage.setImageResource(R.drawable.placeholder_recipe)
                    }
                } else {
                    // Handle regular URL with Coil
                    binding.ivRecipeImage.load(thumbnail) {
                        crossfade(true)
                        placeholder(R.drawable.placeholder_recipe)
                        error(R.drawable.placeholder_recipe)
                        transformations(RoundedCornersTransformation(16f))
                    }
                }
            } else {
                binding.ivRecipeImage.setImageResource(R.drawable.placeholder_recipe)
            }
            
            // Click listener
            binding.root.setOnClickListener {
                onRecipeClick(recipe)
            }
        }
    }
    
    class RecipeDiffCallback : DiffUtil.ItemCallback<RecipeListItem>() {
        override fun areItemsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean {
            return oldItem == newItem
        }
    }
}
