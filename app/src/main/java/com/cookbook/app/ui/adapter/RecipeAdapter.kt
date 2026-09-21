package com.cookbook.app.ui.adapter

import android.content.Context
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.cookbook.app.R
import com.cookbook.app.data.models.RecipeListItem
import com.cookbook.app.databinding.ItemRecipeBinding
import com.cookbook.app.util.CollectionColors
import com.cookbook.app.util.ImageUtils

/**
 * Rezeptliste im Querformat: Foto links, Angaben rechts.
 *
 * Leere Werte werden nie als "0" gezeigt — fehlt die Zeit, entfällt die Angabe.
 */
class RecipeAdapter(
    private val onRecipeClick: (RecipeListItem) -> Unit,
    private val onFavoriteClick: (RecipeListItem) -> Unit
) : ListAdapter<RecipeListItem, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {

    /**
     * Die Eintritts-Animation läuft nur beim ersten Laden, nicht beim Scrollen.
     * Danach bleibt sie aus, bis die Liste neu aufgebaut wird.
     */
    private var animateUntilPosition = -1
    private val animatedPositions = mutableSetOf<Int>()

    fun playEnterAnimation(itemCount: Int) {
        animateUntilPosition = itemCount
        animatedPositions.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding, onRecipeClick, onFavoriteClick)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
        // Jede Zeile steigt genau einmal auf — beim Scrollen und beim Neuzeichnen
        // nach einem Herz-Tipp bleibt sie ruhig.
        if (position < animateUntilPosition && animatedPositions.add(position)) {
            holder.playRise(position)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecipeViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.cancelAnimation()
    }

    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val onRecipeClick: (RecipeListItem) -> Unit,
        private val onFavoriteClick: (RecipeListItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: RecipeListItem) {
            val context = binding.root.context
            val collectionColor = CollectionColors.forName(recipe.collectionLabel)

            binding.tvTitle.text = recipe.title

            // Sammlungs-Marker
            val collectionLabel = recipe.collectionLabel
            if (collectionLabel.isNullOrBlank()) {
                binding.collectionRow.visibility = View.GONE
            } else {
                binding.collectionRow.visibility = View.VISIBLE
                binding.tvCollection.text = collectionLabel
                binding.tvCollection.setTextColor(collectionColor)
                binding.collectionMarker.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(collectionColor)
            }

            // Sachzeile — leer, wenn keine Kategorien hinterlegt sind
            val subtitle = recipe.subtitle
            binding.tvCategories.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            binding.tvCategories.text = subtitle

            // Keine Null-Werte: fehlt die Zeit, entfällt die ganze Angabe
            val hasTime = recipe.totalTime > 0
            binding.iconTime.visibility = if (hasTime) View.VISIBLE else View.GONE
            binding.tvTime.visibility = if (hasTime) View.VISIBLE else View.GONE
            if (hasTime) {
                binding.tvTime.text = context.getString(
                    R.string.minutes_with_unit, recipe.totalTime
                )
            }

            val hasServings = recipe.servings > 0
            binding.iconServings.visibility = if (hasServings) View.VISIBLE else View.GONE
            binding.tvServings.visibility = if (hasServings) View.VISIBLE else View.GONE
            if (hasServings) {
                binding.tvServings.text = recipe.servings.toString()
            }

            bindFavorite(recipe.isFavorite)
            bindImage(recipe.thumbnail, collectionColor)

            binding.cardRecipe.setOnClickListener { onRecipeClick(recipe) }
            binding.btnFavorite.setOnClickListener {
                // Optimistisch schalten; der Aufrufer rollt bei einem Fehler zurück.
                bindFavorite(!recipe.isFavorite)
                onFavoriteClick(recipe)
            }
        }

        private fun bindFavorite(isFavorite: Boolean) {
            val context = binding.root.context
            binding.btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
            )
            binding.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isFavorite) R.color.favorite_active else R.color.favorite_inactive
                )
            )
        }

        /**
         * Ohne Foto trägt die Fläche das Sammlungssymbol in der Sammlungsfarbe —
         * kein für alle Rezepte identisches Platzhalterbild.
         */
        private fun bindImage(thumbnail: String?, collectionColor: Int) {
            val image = binding.ivRecipeImage
            if (thumbnail.isNullOrBlank()) {
                showPlaceholder(image, collectionColor)
                return
            }

            if (ImageUtils.isBase64DataUrl(thumbnail)) {
                // setImageDrawable umgeht Coil — eine noch laufende Anfrage des zuvor
                // gebundenen Rezepts muss abgebrochen werden, sonst landet sie später
                // in diesem wiederverwendeten ViewHolder.
                image.dispose()
                val bitmap = ImageUtils.decodeBase64Image(thumbnail)
                if (bitmap == null) {
                    showPlaceholder(image, collectionColor)
                } else {
                    showPhoto(image)
                    image.setImageDrawable(
                        RoundedBitmapDrawableFactory.create(image.resources, bitmap)
                    )
                }
            } else {
                showPhoto(image)
                image.load(thumbnail) {
                    crossfade(true)
                    listener(
                        onError = { _, _ -> showPlaceholder(image, collectionColor) }
                    )
                }
            }
        }

        private fun showPhoto(image: ImageView) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.imageTintList = null
            image.setBackgroundResource(R.color.photo_placeholder)
        }

        private fun showPlaceholder(image: ImageView, collectionColor: Int) {
            image.dispose()
            image.setBackgroundResource(R.color.photo_placeholder)
            image.scaleType = ImageView.ScaleType.CENTER
            image.setImageResource(R.drawable.ic_chef_hat)
            image.imageTintList = android.content.res.ColorStateList.valueOf(collectionColor)
        }

        /** 12dp nach oben, 450 ms, 55 ms Versatz pro Zeile. */
        fun playRise(position: Int) {
            val context = binding.root.context
            if (animationsDisabled(context)) return

            val rise = 12f * context.resources.displayMetrics.density
            binding.root.alpha = 0f
            binding.root.translationY = rise
            binding.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(position.toLong() * context.resources.getInteger(R.integer.motion_list_stagger_ms))
                .setDuration(context.resources.getInteger(R.integer.motion_list_enter_ms).toLong())
                .start()
        }

        fun cancelAnimation() {
            binding.root.animate().cancel()
            binding.root.alpha = 1f
            binding.root.translationY = 0f
        }

        /** Entspricht prefers-reduced-motion: Systemeinstellung "Animationen aus". */
        private fun animationsDisabled(context: Context): Boolean =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
    }

    class RecipeDiffCallback : DiffUtil.ItemCallback<RecipeListItem>() {
        override fun areItemsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecipeListItem, newItem: RecipeListItem): Boolean =
            oldItem == newItem
    }
}
