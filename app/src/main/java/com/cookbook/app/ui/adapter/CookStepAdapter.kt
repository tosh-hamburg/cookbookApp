package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.data.models.Ingredient
import com.cookbook.app.databinding.ItemCookStepBinding
import com.cookbook.app.databinding.ItemCookStepIngredientBinding
import com.cookbook.app.util.Amounts
import com.cookbook.app.util.RecipeSteps

/**
 * Seiten des Kochmodus — ein Schritt pro Seite.
 *
 * Die Zutatenbox zeigt nur, was im Schritttext vorkommt; findet sich nichts,
 * entfällt die Box.
 */
class CookStepAdapter(
    private val steps: List<RecipeSteps.Step>,
    private val ingredients: List<Ingredient>,
    private val servingsFactorProvider: () -> Double
) : RecyclerView.Adapter<CookStepAdapter.StepViewHolder>() {

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemCookStepBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    inner class StepViewHolder(
        private val binding: ItemCookStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: RecipeSteps.Step) {
            binding.textStepName.visibility = if (step.name == null) View.GONE else View.VISIBLE
            binding.textStepName.text = step.name
            binding.textStepBody.text = step.text

            val matching = RecipeSteps.ingredientsFor(step, ingredients) { it.name }
            binding.stepIngredientsContainer.removeAllViews()

            if (matching.isEmpty()) {
                binding.stepIngredientsBox.visibility = View.GONE
                return
            }

            binding.stepIngredientsBox.visibility = View.VISIBLE
            val factor = servingsFactorProvider()
            val inflater = LayoutInflater.from(binding.root.context)
            matching.forEach { ingredient ->
                val row = ItemCookStepIngredientBinding.inflate(
                    inflater, binding.stepIngredientsContainer, false
                )
                row.textIngredientName.text = ingredient.name
                row.textIngredientAmount.text = Amounts.scale(ingredient.amount, factor)
                binding.stepIngredientsContainer.addView(row.root)
            }
        }
    }
}
