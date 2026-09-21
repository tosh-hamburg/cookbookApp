package com.cookbook.app.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.cookbook.app.R
import com.cookbook.app.data.models.FeaturedRecipe
import com.cookbook.app.databinding.ItemRecipeHeaderBinding
import com.cookbook.app.util.CollectionColors
import com.cookbook.app.util.ImageUtils

/**
 * Kopfbereich der Rezeptliste: "Rezept der Woche" und die Sektionsüberschrift.
 *
 * Läuft als eigener Adapter in einem ConcatAdapter, damit die Rezeptliste selbst
 * eine saubere ListAdapter-Liste bleibt und das Recycling erhalten bleibt.
 */
class RecipeListHeaderAdapter(
    private val onFeaturedClick: (FeaturedRecipe) -> Unit,
    private val onCookNow: (FeaturedRecipe) -> Unit,
    private val onSchedule: (FeaturedRecipe) -> Unit
) : RecyclerView.Adapter<RecipeListHeaderAdapter.HeaderViewHolder>() {

    private var featured: FeaturedRecipe? = null
    private var recipeCount: Int = 0

    fun setFeatured(recipe: FeaturedRecipe?) {
        featured = recipe
        notifyItemChanged(0)
    }

    fun setRecipeCount(count: Int) {
        if (recipeCount == count) return
        recipeCount = count
        notifyItemChanged(0)
    }

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemRecipeHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(featured, recipeCount)
    }

    inner class HeaderViewHolder(
        private val binding: ItemRecipeHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: FeaturedRecipe?, count: Int) {
            val context = binding.root.context
            binding.tvSectionCount.text = context.getString(R.string.recipes_number, count)

            if (recipe == null) {
                binding.featuredContainer.visibility = View.GONE
                return
            }

            binding.featuredContainer.visibility = View.VISIBLE
            binding.tvFeaturedTitle.text = recipe.title

            // Ohne Kochhistorie trägt der Aufmacher keinen Häufigkeitssatz.
            if (recipe.hasCookHistory) {
                binding.tvFeaturedSubtitle.visibility = View.VISIBLE
                binding.tvFeaturedSubtitle.text =
                    context.getString(R.string.cooked_times, recipe.cookCount)
            } else {
                binding.tvFeaturedSubtitle.visibility = View.GONE
            }

            bindImage(binding.ivFeatured, recipe.image, CollectionColors.forName(recipe.collectionLabel))

            binding.cardFeatured.setOnClickListener { onFeaturedClick(recipe) }
            binding.btnCookNow.setOnClickListener { onCookNow(recipe) }
            binding.btnSchedule.setOnClickListener { onSchedule(recipe) }
        }

        private fun bindImage(image: ImageView, source: String?, collectionColor: Int) {
            if (source.isNullOrBlank()) {
                showPlaceholder(image, collectionColor)
                return
            }
            if (ImageUtils.isBase64DataUrl(source)) {
                image.dispose()
                val bitmap = ImageUtils.decodeBase64Image(source)
                if (bitmap == null) {
                    showPlaceholder(image, collectionColor)
                } else {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.imageTintList = null
                    image.setImageDrawable(
                        RoundedBitmapDrawableFactory.create(image.resources, bitmap)
                    )
                }
            } else {
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.imageTintList = null
                image.load(source) {
                    crossfade(true)
                    listener(onError = { _, _ -> showPlaceholder(image, collectionColor) })
                }
            }
        }

        private fun showPlaceholder(image: ImageView, collectionColor: Int) {
            image.dispose()
            image.setBackgroundResource(R.color.photo_placeholder)
            image.scaleType = ImageView.ScaleType.CENTER
            image.setImageResource(R.drawable.ic_chef_hat)
            image.imageTintList = ColorStateList.valueOf(collectionColor)
        }
    }
}
