package com.cookbook.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.R
import com.cookbook.app.data.models.Ingredient
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityRecipeDetailBinding
import com.cookbook.app.databinding.ItemIngredientRowBinding
import com.cookbook.app.databinding.ItemInstructionStepBinding
import com.cookbook.app.databinding.ItemMetricColumnBinding
import com.cookbook.app.ui.adapter.ImagePagerAdapter
import com.cookbook.app.util.Amounts
import com.cookbook.app.util.CollectionColors
import com.cookbook.app.util.RecipeSteps
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Rezeptdetail — Foto, Kennzahlen, Zutaten und Zubereitung.
 */
class RecipeDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"

        /** Grenzen des Portions-Steppers. */
        private const val MIN_SERVINGS = 1
        private const val MAX_SERVINGS = 12
    }

    private lateinit var binding: ActivityRecipeDetailBinding
    private val recipeRepository by lazy { RecipeRepository() }

    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null
    private var currentServings: Int = 4
    private var isFavorite: Boolean = false

    /** Abgehakte Zutaten gelten nur für diese Sitzung. */
    private val checkedIngredients = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)

        setupHeader()
        setupServingsControls()
        setupBottomBar()

        if (recipeId == null) {
            showError("Rezept-ID fehlt")
            finish()
            return
        }
        loadRecipe()
    }

    override fun onResume() {
        super.onResume()
        recipeId?.let { loadRecipe() }
    }

    // ==================== Kopf ====================

    private fun setupHeader() {
        // Die drei Kreise teilen sich eine Drawable-Ressource — ohne mutate()
        // würde das Ausblenden auch andere Views mit diesem Hintergrund treffen.
        listOf(binding.btnBack, binding.btnFavorite, binding.btnOverflow)
            .forEach { it.background = it.background?.mutate() }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnOverflow.setOnClickListener { showOverflowMenu(it) }

        // Eingeklappt trägt die Leiste Papier: Titel wird sichtbar, die weißen
        // Kreise hinter den Knöpfen verschwinden.
        binding.appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
                val range = appBar.totalScrollRange.takeIf { it > 0 } ?: return@OnOffsetChangedListener
                val collapsed = abs(verticalOffset).toFloat() / range

                binding.tvCollapsedTitle.alpha = ((collapsed - 0.6f) / 0.4f).coerceIn(0f, 1f)
                binding.headerTextBlock.alpha = (1f - collapsed * 1.6f).coerceIn(0f, 1f)

                val circleAlpha = ((1f - collapsed * 1.4f).coerceIn(0f, 1f) * 255).toInt()
                listOf(binding.btnBack, binding.btnFavorite, binding.btnOverflow)
                    .forEach { it.background?.alpha = circleAlpha }
            }
        )
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_recipe_detail, menu)
            // Bearbeiten und Wochenplan stehen in der festen Leiste unten.
            menu.findItem(R.id.action_edit)?.isVisible = false
            menu.findItem(R.id.action_add_to_planner)?.isVisible = false
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_collections -> { showManageCollections(); true }
                    R.id.action_gemini -> { sendToGemini(); true }
                    R.id.action_share -> { shareRecipe(); true }
                    R.id.action_delete -> { confirmDelete(); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun setupBottomBar() {
        binding.btnCookMode.setOnClickListener {
            val recipe = currentRecipe ?: return@setOnClickListener
            val intent = Intent(this, CookModeActivity::class.java)
            intent.putExtra(CookModeActivity.EXTRA_RECIPE_ID, recipe.id)
            intent.putExtra(CookModeActivity.EXTRA_SERVINGS, currentServings)
            startActivity(intent)
        }
        binding.btnAddToPlanner.setOnClickListener { showAddToWeekPlanner() }
        binding.btnEdit.setOnClickListener { openEditActivity() }
        binding.btnSendToGemini.setOnClickListener { sendToGemini() }
        binding.noteBlock.setOnClickListener { openEditActivity() }
    }

    // ==================== Portionen ====================

    private fun setupServingsControls() {
        binding.btnDecreaseServings.setOnClickListener { changeServings(-1) }
        binding.btnIncreaseServings.setOnClickListener { changeServings(1) }
    }

    private fun changeServings(delta: Int) {
        val next = (currentServings + delta).coerceIn(MIN_SERVINGS, MAX_SERVINGS)
        if (next == currentServings) return
        currentServings = next
        binding.tvServingsCount.text = currentServings.toString()
        buildIngredientRows()
        buildMetrics()
    }

    // ==================== Laden ====================

    private fun loadRecipe() {
        val id = recipeId ?: return
        lifecycleScope.launch {
            setLoading(true)
            recipeRepository.getRecipe(id)
                .onSuccess { recipe ->
                    setLoading(false)
                    currentRecipe = recipe
                    displayRecipe(recipe)
                }
                .onFailure { error ->
                    setLoading(false)
                    showError(error.message ?: "Rezept konnte nicht geladen werden")
                    finish()
                }
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.tvTitle.text = recipe.title
        binding.tvCollapsedTitle.text = recipe.title

        currentServings = recipe.servings.takeIf { it > 0 }?.coerceIn(MIN_SERVINGS, MAX_SERVINGS) ?: 4
        binding.tvServingsCount.text = currentServings.toString()

        isFavorite = recipe.isFavorite
        renderFavorite()

        bindImages(recipe)
        bindCollectionBadge(recipe)
        buildMetrics()
        bindNote(recipe)
        buildIngredientRows()
        buildSteps(recipe)
        bindCategories(recipe)
        bindSourceUrl(recipe)
    }

    private fun bindImages(recipe: Recipe) {
        val hasImages = recipe.images.isNotEmpty()
        binding.viewPagerImages.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.ivPlaceholder.visibility = if (hasImages) View.GONE else View.VISIBLE
        binding.tabLayoutIndicator.visibility =
            if (recipe.images.size > 1) View.VISIBLE else View.GONE

        if (!hasImages) {
            binding.ivPlaceholder.imageTintList =
                ColorStateList.valueOf(CollectionColors.forName(recipe.collectionLabel))
            return
        }

        binding.viewPagerImages.adapter = ImagePagerAdapter(recipe.images)
        if (recipe.images.size > 1) {
            TabLayoutMediator(binding.tabLayoutIndicator, binding.viewPagerImages) { _, _ -> }.attach()
        }
    }

    private fun bindCollectionBadge(recipe: Recipe) {
        val label = recipe.collectionLabel
        if (label.isNullOrBlank()) {
            binding.tvCollectionBadge.visibility = View.GONE
            return
        }
        binding.tvCollectionBadge.visibility = View.VISIBLE
        binding.tvCollectionBadge.text = label
        binding.tvCollectionBadge.setTextColor(CollectionColors.forName(label))
    }

    /**
     * Metrik-Karte. Spalten mit Wert 0 werden weggelassen, die übrigen verteilen
     * sich gleichmäßig — eine "0" ist keine Information.
     */
    private fun buildMetrics() {
        val recipe = currentRecipe ?: return
        binding.metricsRow.removeAllViews()

        val columns = buildList {
            if (recipe.totalTime > 0) {
                add(recipe.totalTime.toString() to getString(R.string.metric_total))
            }
            if (recipe.activeTime > 0) {
                add(recipe.activeTime.toString() to getString(R.string.metric_work))
            }
            if (recipe.caloriesPerUnit > 0) {
                add(recipe.caloriesPerUnit.toString() to getString(R.string.metric_calories))
            }
            if (recipe.cookCount > 0) {
                add(recipe.cookCount.toString() + getString(R.string.times_short) to getString(R.string.metric_cooked))
            }
        }

        binding.metricsRow.visibility = if (columns.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        columns.forEach { (value, label) ->
            val column = ItemMetricColumnBinding.inflate(inflater, binding.metricsRow, false)
            column.tvMetricValue.text = value
            column.tvMetricLabel.text = label
            column.root.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            binding.metricsRow.addView(column.root)
        }
    }

    private fun bindNote(recipe: Recipe) {
        val note = recipe.notes?.trim()
        binding.noteBlock.visibility = if (note.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.tvNote.text = note
    }

    // ==================== Zutaten ====================

    /**
     * Mengen werden mit dem Verhältnis der Portionen umgerechnet. Nicht
     * parsebare Angaben bleiben stehen; kcal pro Portion ändert sich nicht.
     */
    private fun buildIngredientRows() {
        val recipe = currentRecipe ?: return
        val container = binding.ingredientsContainer
        container.removeAllViews()

        val factor = Amounts.factor(currentServings, recipe.servings)
        val inflater = LayoutInflater.from(this)

        recipe.ingredients.forEachIndexed { index, ingredient ->
            val row = ItemIngredientRowBinding.inflate(inflater, container, false)
            bindIngredientRow(row, index, ingredient, factor)
            container.addView(row.root)

            if (index < recipe.ingredients.lastIndex) {
                container.addView(createDivider())
            }
        }
    }

    private fun bindIngredientRow(
        row: ItemIngredientRowBinding,
        index: Int,
        ingredient: Ingredient,
        factor: Double
    ) {
        row.tvIngredientName.text = ingredient.name
        row.tvIngredientAmount.text = Amounts.scale(ingredient.amount, factor)
        renderChecked(row, checkedIngredients.contains(index))

        // Die ganze Zeile ist das Touch-Target.
        row.ingredientRow.setOnClickListener {
            val nowChecked = !checkedIngredients.contains(index)
            if (nowChecked) checkedIngredients.add(index) else checkedIngredients.remove(index)
            renderChecked(row, nowChecked)
        }
    }

    private fun renderChecked(row: ItemIngredientRowBinding, isChecked: Boolean) {
        row.checkbox.setBackgroundResource(
            if (isChecked) R.drawable.bg_checkbox_checked else R.drawable.bg_checkbox_unchecked
        )
        val nameColor = ContextCompat.getColor(
            this, if (isChecked) R.color.ingredient_checked else R.color.text_primary
        )
        val amountColor = ContextCompat.getColor(
            this, if (isChecked) R.color.ingredient_checked else R.color.text_secondary
        )
        row.tvIngredientName.setTextColor(nameColor)
        row.tvIngredientAmount.setTextColor(amountColor)
        row.tvIngredientName.paintFlags = if (isChecked) {
            row.tvIngredientName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            row.tvIngredientName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun createDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (1 * resources.displayMetrics.density).toInt()
        )
        setBackgroundColor(ContextCompat.getColor(this@RecipeDetailActivity, R.color.card_stroke))
    }

    // ==================== Zubereitung ====================

    private fun buildSteps(recipe: Recipe) {
        val container = binding.stepsContainer
        container.removeAllViews()

        val steps = RecipeSteps.parse(recipe.instructions)
        val hasSteps = steps.isNotEmpty()
        binding.tvInstructionsHeader.visibility = if (hasSteps) View.VISIBLE else View.GONE
        if (!hasSteps) return

        val inflater = LayoutInflater.from(this)
        steps.forEach { step ->
            val view = ItemInstructionStepBinding.inflate(inflater, container, false)
            view.tvStepNumber.text = step.label
            view.tvStepText.text = step.text

            val hasHead = step.name != null || step.minutes != null
            view.stepHeadRow.visibility = if (hasHead) View.VISIBLE else View.GONE
            view.tvStepName.text = step.name.orEmpty()
            view.tvStepDuration.visibility = if (step.minutes == null) View.GONE else View.VISIBLE
            step.minutes?.let {
                view.tvStepDuration.text = getString(R.string.minutes_with_unit, it)
            }

            view.stepDivider.visibility = if (step.index == steps.lastIndex) View.GONE else View.VISIBLE
            container.addView(view.root)
        }
    }

    private fun bindCategories(recipe: Recipe) {
        binding.chipGroupCategories.removeAllViews()
        recipe.categoryNames.forEach { name ->
            val chip = Chip(this).apply {
                setChipDrawable(
                    com.google.android.material.chip.ChipDrawable.createFromAttributes(
                        this@RecipeDetailActivity, null, 0, R.style.Theme_Cookbook_Chip_Static
                    )
                )
                text = name
                isClickable = false
                isCheckable = false
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun bindSourceUrl(recipe: Recipe) {
        val url = recipe.sourceUrl
        if (url.isNullOrEmpty()) {
            binding.btnSourceUrl.visibility = View.GONE
            return
        }
        binding.btnSourceUrl.visibility = View.VISIBLE
        binding.btnSourceUrl.setOnClickListener { openUrl(url) }
    }

    // ==================== Favorit ====================

    /** Optimistisch schalten, bei einem Fehler zurückrollen. */
    private fun toggleFavorite() {
        val id = currentRecipe?.id ?: return
        val previous = isFavorite
        isFavorite = !previous
        renderFavorite()

        lifecycleScope.launch {
            recipeRepository.setFavorite(id, isFavorite)
                .onSuccess { response ->
                    isFavorite = response.isFavorite
                    currentRecipe = currentRecipe?.copy(isFavorite = response.isFavorite)
                    renderFavorite()
                }
                .onFailure {
                    isFavorite = previous
                    renderFavorite()
                    showError(getString(R.string.favorite_failed))
                }
        }
    }

    private fun renderFavorite() {
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
        binding.btnFavorite.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this, if (isFavorite) R.color.primary else R.color.favorite_inactive
            )
        )
    }

    // ==================== Aktionen ====================

    private fun showManageCollections() {
        val recipe = currentRecipe ?: return
        ManageCollectionsBottomSheet.newInstance(recipe).apply {
            onCollectionsUpdated = { loadRecipe() }
        }.show(supportFragmentManager, "ManageCollections")
    }

    private fun showAddToWeekPlanner() {
        val recipe = currentRecipe ?: return
        AddToWeekPlannerBottomSheet.newInstance(recipe)
            .show(supportFragmentManager, "AddToWeekPlanner")
    }

    private fun scaledIngredients(recipe: Recipe): List<Ingredient> {
        val factor = Amounts.factor(currentServings, recipe.servings)
        return recipe.ingredients.map { it.copy(amount = Amounts.scale(it.amount, factor)) }
    }

    private fun sendToGemini() {
        val recipe = currentRecipe ?: return

        val ingredientsList = scaledIngredients(recipe)
            .joinToString("\n") { if (it.amount.isNotEmpty()) "${it.amount} ${it.name}" else it.name }

        val portionLabel = if (currentServings == 1) {
            getString(R.string.portion_singular)
        } else {
            getString(R.string.portion_plural)
        }

        val prompt = """Füge bitte folgende Zutaten zu meiner Einkaufsliste in Google Keep hinzu (erstelle die Liste "Einkaufsliste" falls sie nicht existiert):

${recipe.title} ($currentServings $portionLabel):
$ingredientsList"""

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Prompt", prompt))

        AlertDialog.Builder(this)
            .setTitle(R.string.gemini_prompt_copied)
            .setMessage(R.string.gemini_prompt_description)
            .setPositiveButton(R.string.open_gemini) { _, _ -> openGemini() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openGemini() {
        val geminiPackages = listOf(
            "com.google.android.apps.bard",
            "com.google.android.apps.googleassistant"
        )

        for (packageName in geminiPackages) {
            val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(packageName) }
                .getOrNull()
            if (launchIntent != null) {
                startActivity(launchIntent)
                return
            }
        }

        runCatching {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app"))
            startActivity(Intent.createChooser(browserIntent, getString(R.string.open_gemini)))
        }.onFailure { showError(getString(R.string.gemini_not_found)) }
    }

    private fun openEditActivity() {
        val recipe = currentRecipe ?: return
        val intent = Intent(this, RecipeEditActivity::class.java)
        intent.putExtra(RecipeEditActivity.EXTRA_RECIPE_ID, recipe.id)
        startActivity(intent)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recipe)
            .setMessage(R.string.delete_recipe_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteRecipe() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRecipe() {
        val id = recipeId ?: return
        lifecycleScope.launch {
            setLoading(true)
            recipeRepository.deleteRecipe(id)
                .onSuccess {
                    Toast.makeText(this@RecipeDetailActivity, R.string.recipe_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .onFailure { error ->
                    setLoading(false)
                    showError(error.message ?: "Rezept konnte nicht gelöscht werden")
                }
        }
    }

    private fun shareRecipe() {
        val recipe = currentRecipe ?: return
        val shareText = buildString {
            appendLine(recipe.title)
            appendLine()
            appendLine(getString(R.string.ingredients) + ":")
            scaledIngredients(recipe).forEach { appendLine("• ${it.amount} ${it.name}") }
            appendLine()
            appendLine(getString(R.string.instructions) + ":")
            appendLine(recipe.instructions)
            if (!recipe.sourceUrl.isNullOrEmpty()) {
                appendLine()
                appendLine("Quelle: ${recipe.sourceUrl}")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, recipe.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { showError("URL konnte nicht geöffnet werden") }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.contentContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
