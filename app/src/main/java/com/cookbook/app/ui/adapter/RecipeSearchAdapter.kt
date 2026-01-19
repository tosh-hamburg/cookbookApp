package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.util.ImageUtils
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.databinding.ItemRecipeSearchBinding

/**
 * Adapter for recipe search dialog in weekly planner
 */
class RecipeSearchAdapter(
    private val onRecipeClick: (RecipeListItem) -> Unit
) : ListAdapter<RecipeListItem, RecipeSearchAdapter.ViewHolder>(RecipeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecipeSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRecipeClick(getItem(position))
                }
            }
        }

        fun bind(recipe: RecipeListItem) {
            binding.apply {
                textRecipeTitle.text = recipe.title
                
                // Time
                val totalTime = recipe.totalTime
                textTime.text = if (totalTime > 0) {
                    root.context.getString(R.string.minutes_format, totalTime)
                } else {
                    "-"
                }
                
                // Servings
                textServings.text = root.context.getString(R.string.portions_format, recipe.servings)
                
                // Categories
                textCategories.text = recipe.categories.joinToString(", ")
                
                // Image (handles both Base64 and URLs)
                ImageUtils.loadImage(
                    imageRecipe,
                    recipe.thumbnail,
                    R.drawable.placeholder_recipe
                )
            }
        }
    }

    private class RecipeDiffCallback : DiffUtil.ItemCallback<RecipeListItem>() {
        override fun areItemsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean {
            return oldItem == newItem
        }
    }
}
