package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.data.models.IngredientRequest
import com.cookbook.app.databinding.ItemIngredientEditBinding

/**
 * Adapter for displaying and managing ingredients in the recipe edit screen
 */
class EditIngredientAdapter(
    private val onEditClick: (Int, IngredientRequest) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<EditIngredientAdapter.IngredientViewHolder>() {

    private val ingredients = mutableListOf<IngredientRequest>()

    fun submitList(newIngredients: List<IngredientRequest>) {
        ingredients.clear()
        ingredients.addAll(newIngredients)
        notifyDataSetChanged()
    }

    fun addIngredient(ingredient: IngredientRequest) {
        ingredients.add(ingredient)
        notifyItemInserted(ingredients.size - 1)
    }

    fun updateIngredient(position: Int, ingredient: IngredientRequest) {
        if (position in ingredients.indices) {
            ingredients[position] = ingredient
            notifyItemChanged(position)
        }
    }

    fun removeIngredient(position: Int) {
        if (position in ingredients.indices) {
            ingredients.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, ingredients.size)
        }
    }

    fun clearAll() {
        val size = ingredients.size
        ingredients.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getIngredients(): List<IngredientRequest> = ingredients.toList()

    fun isEmpty(): Boolean = ingredients.isEmpty()

    override fun getItemCount(): Int = ingredients.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemIngredientEditBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IngredientViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position], position + 1)
    }

    class IngredientViewHolder(
        private val binding: ItemIngredientEditBinding,
        private val onEditClick: (Int, IngredientRequest) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: IngredientRequest, number: Int) {
            binding.tvNumber.text = "$number."
            binding.tvAmount.text = ingredient.amount
            binding.tvName.text = ingredient.name
            
            binding.btnEdit.setOnClickListener {
                onEditClick(adapterPosition, ingredient)
            }
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(adapterPosition)
            }
            
            // Also allow editing by clicking on the row
            binding.root.setOnClickListener {
                onEditClick(adapterPosition, ingredient)
            }
        }
    }
}
