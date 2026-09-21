package com.cookbook.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.content.res.ColorStateList
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.R
import com.cookbook.app.util.CollectionColors
import com.cookbook.app.util.ImageUtils
import com.cookbook.app.data.models.*
import com.cookbook.app.data.repository.MealPlanRepository
import com.cookbook.app.databinding.ActivityWeeklyPlannerBinding
import com.cookbook.app.databinding.ItemDayPlanBinding
import com.cookbook.app.databinding.ItemIngredientAggregatedBinding
import com.cookbook.app.databinding.ItemMealSlotBinding
import kotlinx.coroutines.launch
import java.util.*

/**
 * Activity for weekly meal planning
 */
class WeeklyPlannerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WeeklyPlannerActivity"
        private const val DEFAULT_GEMINI_PROMPT = "Füge bitte folgende Zutaten zu meiner Einkaufsliste in Google Keep hinzu (erstelle die Liste \"Einkaufsliste\" falls sie nicht existiert):"
    }

    private lateinit var binding: ActivityWeeklyPlannerBinding
    private val mealPlanRepository by lazy { MealPlanRepository() }

    private var currentWeekStart: Date = WeekPlan.getCurrentWeekStart()
    private var weekPlan: WeekPlan = WeekPlan.createEmpty(currentWeekStart)
    private var isLoading: Boolean = false

    /** Der Einkaufszettel liegt zusammengeklappt unter der Leiste am unteren Rand. */
    private var isShoppingListExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyPlannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNavigation()
        setupGeminiButton()
        loadMealPlan()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupNavigation() {
        binding.btnPreviousWeek.setOnClickListener {
            if (!isLoading) navigateToWeek(-7)
        }

        binding.btnNextWeek.setOnClickListener {
            if (!isLoading) navigateToWeek(7)
        }
    }

    private fun setupGeminiButton() {
        // Die Sendeknöpfe hängen an updateIngredientsCard(); hier nur die Leiste.
        binding.btnOpenShoppingList.setOnClickListener { toggleShoppingList() }
    }

    private fun toggleShoppingList() {
        isShoppingListExpanded = !isShoppingListExpanded
        binding.cardIngredients.visibility =
            if (isShoppingListExpanded) View.VISIBLE else View.GONE

        if (isShoppingListExpanded) {
            binding.cardIngredients.post {
                binding.scrollContent.smoothScrollTo(0, binding.cardIngredients.top)
            }
        }
    }

    private fun navigateToWeek(days: Int) {
        val calendar = Calendar.getInstance().apply {
            time = currentWeekStart
            add(Calendar.DAY_OF_MONTH, days)
        }
        currentWeekStart = calendar.time
        loadMealPlan()
    }

    private fun loadMealPlan() {
        setLoading(true)
        weekPlan = WeekPlan.createEmpty(currentWeekStart)
        updateWeekHeader()

        lifecycleScope.launch {
            val result = mealPlanRepository.getMealPlan(currentWeekStart)

            result.onSuccess { response ->
                weekPlan = weekPlan.updateFromResponse(response)
                updateUI()
            }.onFailure { _ ->
                Toast.makeText(
                    this@WeeklyPlannerActivity,
                    getString(R.string.error_loading_meal_plan),
                    Toast.LENGTH_SHORT
                ).show()
                updateUI()
            }

            setLoading(false)
        }
    }

    private fun updateWeekHeader() {
        binding.textCalendarWeek.text = getString(R.string.calendar_week, weekPlan.getWeekNumber())
        binding.textWeekRange.text = getString(
            R.string.week_meals_summary,
            weekPlan.getFormattedRange(),
            weekPlan.getTotalMealsPlanned()
        )
    }

    private fun isSameWeek(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
    }

    private fun updateUI() {
        updateWeekHeader()
        buildDayViews()
        updateIngredientsCard()
        updateEmptyState()
    }

    private fun buildDayViews() {
        binding.daysContainer.removeAllViews()

        for (day in weekPlan.days) {
            val dayBinding = ItemDayPlanBinding.inflate(
                LayoutInflater.from(this),
                binding.daysContainer,
                false
            )

            dayBinding.textDayName.text = day.getDayName(this)
            dayBinding.textDate.text = day.getFormattedDate()
            bindDayStatus(dayBinding, day)

            dayBinding.mealSlotsContainer.removeAllViews()

            for (mealType in listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
                val mealSlot = day.getMeal(mealType)
                val slotBinding = ItemMealSlotBinding.inflate(
                    LayoutInflater.from(this),
                    dayBinding.mealSlotsContainer,
                    false
                )

                setupMealSlotView(slotBinding, mealSlot, day.dayIndex, day.getDayName(this))
                dayBinding.mealSlotsContainer.addView(slotBinding.root)
            }

            binding.daysContainer.addView(dayBinding.root)
        }
    }

    /** "geplant", "{n} offen" oder "ganz offen" — je nachdem, wie voll der Tag ist. */
    private fun bindDayStatus(dayBinding: ItemDayPlanBinding, day: DayPlan) {
        val planned = day.meals.values.count { it.recipe != null }
        val open = day.meals.size - planned

        val (text, colorRes) = when {
            open == 0 -> getString(R.string.status_planned) to R.color.secondary
            planned == 0 -> getString(R.string.status_fully_open) to R.color.status_open
            else -> getString(R.string.status_open_count, open) to R.color.text_hint
        }
        dayBinding.textDayStatus.text = text
        dayBinding.textDayStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    /**
     * Belegt: weiße Zeile mit Sammlungs-Tile oder Rezeptfoto.
     * Leer: gestrichelter Rahmen mit Plus. Ein Tipp öffnet das Slot-Detail
     * beziehungsweise direkt die Rezeptauswahl.
     */
    private fun setupMealSlotView(
        slotBinding: ItemMealSlotBinding,
        mealSlot: MealSlot,
        dayIndex: Int,
        dayName: String
    ) {
        val mealType = mealSlot.mealType
        slotBinding.textMealType.text = mealType.getLabel(this)

        val recipe = mealSlot.recipe
        if (recipe == null) {
            slotBinding.slotRoot.setBackgroundResource(R.drawable.bg_slot_empty)
            slotBinding.slotTile.backgroundTintList =
                ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            slotBinding.iconMealType.visibility = View.VISIBLE
            slotBinding.iconMealType.setImageResource(R.drawable.ic_add)
            slotBinding.iconMealType.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_hint))
            slotBinding.imageRecipe.visibility = View.GONE
            slotBinding.textRecipeTitle.setText(R.string.choose_recipe)
            slotBinding.textRecipeTitle.setTextAppearance(R.style.TextAppearance_Cookbook_Body)
            slotBinding.textRecipeTitle.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            slotBinding.textTime.visibility = View.GONE
            slotBinding.iconTrailing.setImageResource(R.drawable.ic_add)

            slotBinding.slotRoot.setOnClickListener {
                // Angetippt: kurz hervorheben, dann die Auswahl öffnen.
                slotBinding.slotRoot.setBackgroundResource(R.drawable.bg_slot_selected)
                openRecipeSearch(dayIndex, mealType, dayName)
            }
            return
        }

        val collectionColor = CollectionColors.forName(recipe.categories.firstOrNull())
        slotBinding.slotRoot.setBackgroundResource(R.drawable.bg_slot_filled)
        slotBinding.slotTile.backgroundTintList = ColorStateList.valueOf(CollectionColors.tint(collectionColor))
        slotBinding.textRecipeTitle.setTextAppearance(R.style.TextAppearance_Cookbook_SlotTitle)
        slotBinding.textRecipeTitle.text = recipe.title

        val thumbnail = recipe.firstImage
        if (thumbnail.isNullOrBlank()) {
            slotBinding.imageRecipe.visibility = View.GONE
            slotBinding.iconMealType.visibility = View.VISIBLE
            slotBinding.iconMealType.setImageResource(getMealTypeIcon(mealType))
            slotBinding.iconMealType.imageTintList = ColorStateList.valueOf(collectionColor)
        } else {
            slotBinding.iconMealType.visibility = View.GONE
            slotBinding.imageRecipe.visibility = View.VISIBLE
            ImageUtils.loadImage(slotBinding.imageRecipe, thumbnail, R.drawable.placeholder_recipe)
        }

        if (recipe.totalTime > 0) {
            slotBinding.textTime.visibility = View.VISIBLE
            slotBinding.textTime.text = getString(R.string.minutes_with_unit, recipe.totalTime)
        } else {
            slotBinding.textTime.visibility = View.GONE
        }
        slotBinding.iconTrailing.setImageResource(R.drawable.ic_chevron_right)

        slotBinding.slotRoot.setOnClickListener {
            openSlotDetail(dayIndex, mealType, dayName, mealSlot)
        }
    }

    /** Portionen, Rezept öffnen, entfernen — alles im Slot-Detail. */
    private fun openSlotDetail(
        dayIndex: Int,
        mealType: MealType,
        dayName: String,
        mealSlot: MealSlot
    ) {
        val recipe = mealSlot.recipe ?: return
        MealSlotBottomSheet.newInstance(
            dayName = dayName,
            mealType = mealType,
            recipeTitle = recipe.title,
            servings = mealSlot.servings,
            onServingsChanged = { servings ->
                updateServings(dayIndex, mealType, servings, recipe.id)
            },
            onOpenRecipe = { openRecipeDetail(recipe.id) },
            onRemove = { removeRecipeFromSlot(dayIndex, mealType) }
        ).show(supportFragmentManager, "mealSlot")
    }

    private fun getMealTypeIcon(mealType: MealType): Int {
        return when (mealType) {
            MealType.BREAKFAST -> R.drawable.ic_breakfast
            MealType.LUNCH -> R.drawable.ic_lunch
            MealType.DINNER -> R.drawable.ic_dinner
        }
    }

    private fun openRecipeSearch(dayIndex: Int, mealType: MealType, dayName: String) {
        val bottomSheet = RecipeSearchBottomSheet.newInstance(
            dayIndex = dayIndex,
            mealType = mealType,
            dayName = dayName
        ) { recipe, selectedDayIndex, selectedMealType ->
            addRecipeToSlot(selectedDayIndex, selectedMealType, recipe)
        }
        bottomSheet.show(supportFragmentManager, "recipe_search")
    }

    private fun addRecipeToSlot(dayIndex: Int, mealType: MealType, recipe: RecipeListItem) {
        val servings = 2 // Default servings

        // Update local state immediately
        val day = weekPlan.days[dayIndex]
        day.setMeal(mealType, MealSlot(
            mealType = mealType,
            recipe = MealRecipeResponse(
                id = recipe.id,
                title = recipe.title,
                images = if (recipe.thumbnail != null) listOf(recipe.thumbnail) else emptyList(),
                servings = recipe.servings,
                totalTime = recipe.totalTime,
                categories = recipe.categories
            ),
            servings = servings
        ))
        updateUI()

        // Save to backend
        lifecycleScope.launch {
            val result = mealPlanRepository.updateMealSlot(
                weekStart = currentWeekStart,
                dayIndex = dayIndex,
                mealType = mealType,
                recipeId = recipe.id,
                servings = servings
            )

            result.onFailure {
                Toast.makeText(
                    this@WeeklyPlannerActivity,
                    getString(R.string.error_saving_meal),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeRecipeFromSlot(dayIndex: Int, mealType: MealType) {
        // Update local state immediately
        val day = weekPlan.days[dayIndex]
        day.setMeal(mealType, MealSlot(mealType = mealType))
        updateUI()

        // Save to backend
        lifecycleScope.launch {
            val result = mealPlanRepository.updateMealSlot(
                weekStart = currentWeekStart,
                dayIndex = dayIndex,
                mealType = mealType,
                recipeId = null,
                servings = 2
            )

            result.onSuccess {
                Toast.makeText(
                    this@WeeklyPlannerActivity,
                    getString(R.string.meal_removed),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@WeeklyPlannerActivity,
                    getString(R.string.error_saving_meal),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateServings(dayIndex: Int, mealType: MealType, newServings: Int, recipeId: String) {
        if (newServings < 1 || newServings > 99) return

        // Update local state immediately
        val day = weekPlan.days[dayIndex]
        val currentMeal = day.getMeal(mealType)
        day.setMeal(mealType, currentMeal.copy(servings = newServings))
        updateUI()

        // Save to backend
        lifecycleScope.launch {
            mealPlanRepository.updateMealSlot(
                weekStart = currentWeekStart,
                dayIndex = dayIndex,
                mealType = mealType,
                recipeId = recipeId,
                servings = newServings
            )
        }
    }

    private fun openRecipeDetail(recipeId: String) {
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        startActivity(intent)
    }

    private fun updateIngredientsCard() {
        val allIngredients = aggregateIngredients()
        
        // Filter out excluded ingredients
        val visibleIngredients = allIngredients.filter { 
            !weekPlan.excludedIngredients.contains(it.name.lowercase()) 
        }
        
        if (visibleIngredients.isEmpty() && weekPlan.excludedIngredients.isEmpty()) {
            binding.cardIngredients.visibility = View.GONE
            binding.shoppingBar.visibility = View.GONE
            return
        }

        // Die Leiste fasst zusammen; der Inhalt klappt auf Wunsch darüber auf.
        binding.shoppingBar.visibility = View.VISIBLE
        binding.textShoppingSummary.text = getString(
            R.string.shopping_positions,
            visibleIngredients.size,
            weekPlan.getTotalMealsPlanned()
        )
        binding.cardIngredients.visibility =
            if (isShoppingListExpanded) View.VISIBLE else View.GONE
        
        // Separate into new and already sent
        val newIngredients = visibleIngredients.filter { 
            !weekPlan.sentIngredients.contains(it.name.lowercase()) 
        }
        val sentIngredients = visibleIngredients.filter { 
            weekPlan.sentIngredients.contains(it.name.lowercase()) 
        }
        
        // Update count badge
        binding.textIngredientCount.text = getString(R.string.new_sent_format, newIngredients.size, sentIngredients.size)
        
        // Show/hide restore excluded button
        binding.btnRestoreExcluded.visibility = if (weekPlan.excludedIngredients.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnRestoreExcluded.text = getString(R.string.restore_excluded) + " (${weekPlan.excludedIngredients.size})"
        binding.btnRestoreExcluded.setOnClickListener { resetExcludedIngredients() }
        
        // Show/hide reset sent button
        binding.btnResetSent.visibility = if (sentIngredients.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnResetSent.text = getString(R.string.reset_sent) + " (${sentIngredients.size})"
        binding.btnResetSent.setOnClickListener { resetSentIngredients() }
        
        // Build new ingredients list
        binding.sectionNewIngredients.visibility = if (newIngredients.isNotEmpty()) View.VISIBLE else View.GONE
        binding.newIngredientsContainer.removeAllViews()
        for (ingredient in newIngredients) {
            addIngredientView(binding.newIngredientsContainer, ingredient, isSent = false)
        }
        
        // Build sent ingredients list
        binding.sectionSentIngredients.visibility = if (sentIngredients.isNotEmpty()) View.VISIBLE else View.GONE
        binding.sentIngredientsContainer.removeAllViews()
        for (ingredient in sentIngredients) {
            addIngredientView(binding.sentIngredientsContainer, ingredient, isSent = true)
        }
        
        // Update send buttons
        binding.btnSendNewToGemini.visibility = if (newIngredients.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnSendNewToGemini.text = getString(R.string.send_new_ingredients) + " (${newIngredients.size})"
        binding.btnSendNewToGemini.setOnClickListener { sendIngredientsToGemini(onlyNew = true) }
        
        binding.btnSendToGemini.text = getString(R.string.send_all_ingredients) + " (${visibleIngredients.size})"
        binding.btnSendToGemini.setOnClickListener { sendIngredientsToGemini(onlyNew = false) }
    }
    
    private fun addIngredientView(container: android.widget.LinearLayout, ingredient: AggregatedIngredient, isSent: Boolean) {
        val ingredientBinding = ItemIngredientAggregatedBinding.inflate(
            LayoutInflater.from(this),
            container,
            false
        )
        ingredientBinding.textAmount.text = ingredient.totalAmount.ifEmpty { "–" }
        ingredientBinding.textName.text = ingredient.name
        ingredientBinding.iconSent.visibility = if (isSent) View.VISIBLE else View.GONE
        
        // Set up delete button
        ingredientBinding.btnAction.setImageResource(R.drawable.ic_delete)
        ingredientBinding.btnAction.setOnClickListener {
            excludeIngredient(ingredient.name)
        }
        
        // Dim sent ingredients slightly
        if (isSent) {
            ingredientBinding.root.alpha = 0.7f
        }
        
        container.addView(ingredientBinding.root)
    }
    
    private fun excludeIngredient(ingredientName: String) {
        val normalizedName = ingredientName.lowercase()
        
        // Update local state immediately
        weekPlan = weekPlan.copy(
            excludedIngredients = weekPlan.excludedIngredients + normalizedName
        )
        updateIngredientsCard()
        
        // Save to backend
        lifecycleScope.launch {
            val result = mealPlanRepository.excludeIngredients(currentWeekStart, listOf(normalizedName))
            result.onSuccess {
                Toast.makeText(this@WeeklyPlannerActivity, getString(R.string.ingredient_excluded), Toast.LENGTH_SHORT).show()
            }.onFailure {
                // Revert on error
                weekPlan = weekPlan.copy(
                    excludedIngredients = weekPlan.excludedIngredients - normalizedName
                )
                updateIngredientsCard()
            }
        }
    }
    
    private fun resetExcludedIngredients() {
        val previousExcluded = weekPlan.excludedIngredients
        
        // Update local state immediately
        weekPlan = weekPlan.copy(excludedIngredients = emptySet())
        updateIngredientsCard()
        
        // Save to backend
        lifecycleScope.launch {
            val result = mealPlanRepository.resetExcludedIngredients(currentWeekStart)
            result.onSuccess {
                Toast.makeText(this@WeeklyPlannerActivity, getString(R.string.excluded_reset), Toast.LENGTH_SHORT).show()
            }.onFailure {
                // Revert on error
                weekPlan = weekPlan.copy(excludedIngredients = previousExcluded)
                updateIngredientsCard()
            }
        }
    }
    
    private fun resetSentIngredients() {
        val previousSent = weekPlan.sentIngredients
        
        // Update local state immediately
        weekPlan = weekPlan.copy(sentIngredients = emptySet())
        updateIngredientsCard()
        
        // Save to backend
        lifecycleScope.launch {
            val result = mealPlanRepository.resetSentIngredients(currentWeekStart)
            result.onSuccess {
                Toast.makeText(this@WeeklyPlannerActivity, getString(R.string.sent_reset), Toast.LENGTH_SHORT).show()
            }.onFailure {
                // Revert on error
                weekPlan = weekPlan.copy(sentIngredients = previousSent)
                updateIngredientsCard()
            }
        }
    }

    private fun aggregateIngredients(): List<AggregatedIngredient> {
        val ingredientMap = mutableMapOf<String, MutableList<Pair<Double, String>>>()
        val sourceMap = mutableMapOf<String, MutableList<IngredientSource>>()

        for (day in weekPlan.days) {
            for (mealType in listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
                val meal = day.getMeal(mealType)
                val recipe = meal.recipe ?: continue

                val scaleFactor = meal.servings.toDouble() / (recipe.servings.takeIf { it > 0 } ?: 1)

                for (ingredient in recipe.ingredients) {
                    val normalizedName = ingredient.name.lowercase().trim()
                    val parsed = parseAmount(ingredient.amount)
                    val scaledValue = parsed.first * scaleFactor

                    ingredientMap.getOrPut(normalizedName) { mutableListOf() }
                        .add(Pair(scaledValue, parsed.second))

                    sourceMap.getOrPut(normalizedName) { mutableListOf() }
                        .add(IngredientSource(
                            recipeTitle = recipe.title,
                            servings = meal.servings,
                            originalAmount = ingredient.amount
                        ))
                }
            }
        }

        return ingredientMap.map { (name, amounts) ->
            val unitGroups = mutableMapOf<String, Double>()
            for ((value, unit) in amounts) {
                val normalizedUnit = unit.lowercase()
                unitGroups[normalizedUnit] = (unitGroups[normalizedUnit] ?: 0.0) + value
            }

            val totalAmount = if (unitGroups.size == 1) {
                val (unit, value) = unitGroups.entries.first()
                if (value > 0) "${formatNumber(value)} $unit".trim() else unit
            } else {
                unitGroups.entries
                    .map { (unit, value) -> if (value > 0) "${formatNumber(value)} $unit".trim() else unit }
                    .joinToString(" + ")
            }

            AggregatedIngredient(
                name = name.replaceFirstChar { it.uppercase() },
                totalAmount = totalAmount,
                sources = sourceMap[name] ?: emptyList()
            )
        }.sortedBy { it.name }
    }

    private fun parseAmount(amount: String): Pair<Double, String> {
        if (amount.isEmpty()) return Pair(0.0, "")

        // Handle fractions like "1/2" or "1 1/2"
        val fractionRegex = Regex("""(\d+)?\s*(\d+)/(\d+)\s*(.*)""")
        fractionRegex.matchEntire(amount)?.let { match ->
            val whole = match.groupValues[1].toIntOrNull() ?: 0
            val num = match.groupValues[2].toIntOrNull() ?: 0
            val denom = match.groupValues[3].toIntOrNull() ?: 1
            val unit = match.groupValues[4].trim()
            return Pair(whole + (num.toDouble() / denom), unit)
        }

        // Handle decimals and whole numbers
        val numberRegex = Regex("""^([\d.,]+)\s*(.*)""")
        numberRegex.matchEntire(amount)?.let { match ->
            val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            val unit = match.groupValues[2].trim()
            return Pair(value, unit)
        }

        return Pair(0.0, amount)
    }

    private fun formatNumber(num: Double): String {
        if (num == 0.0) return ""
        if (kotlin.math.abs(num - kotlin.math.round(num)) < 0.01) {
            return kotlin.math.round(num).toInt().toString()
        }

        val remainder = num % 1
        val whole = kotlin.math.floor(num).toInt()

        return when {
            kotlin.math.abs(remainder - 0.5) < 0.05 -> if (whole > 0) "${whole}½" else "½"
            kotlin.math.abs(remainder - 0.25) < 0.05 -> if (whole > 0) "${whole}¼" else "¼"
            kotlin.math.abs(remainder - 0.75) < 0.05 -> if (whole > 0) "${whole}¾" else "¾"
            kotlin.math.abs(remainder - 0.333) < 0.03 -> if (whole > 0) "${whole}⅓" else "⅓"
            kotlin.math.abs(remainder - 0.666) < 0.03 -> if (whole > 0) "${whole}⅔" else "⅔"
            else -> String.format(Locale.GERMANY, "%.1f", num).replace(",0", "")
        }
    }

    private fun updateEmptyState() {
        val hasMeals = weekPlan.getTotalMealsPlanned() > 0
        binding.emptyStateContainer.visibility = if (hasMeals) View.GONE else View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.daysContainer.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun sendIngredientsToGemini(onlyNew: Boolean = false) {
        val allIngredients = aggregateIngredients()
        
        // Filter out excluded ingredients
        val visibleIngredients = allIngredients.filter { 
            !weekPlan.excludedIngredients.contains(it.name.lowercase()) 
        }
        
        // Get ingredients to send
        val ingredientsToSend = if (onlyNew) {
            visibleIngredients.filter { !weekPlan.sentIngredients.contains(it.name.lowercase()) }
        } else {
            visibleIngredients
        }

        if (ingredientsToSend.isEmpty()) {
            Toast.makeText(this, "Keine Zutaten vorhanden", Toast.LENGTH_SHORT).show()
            return
        }

        val ingredientsList = ingredientsToSend
            .map { if (it.totalAmount.isNotEmpty()) "${it.totalAmount} ${it.name}" else it.name }
            .joinToString("\n")

        val prompt = "$DEFAULT_GEMINI_PROMPT\n\n$ingredientsList"

        // Copy to clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Einkaufsliste", prompt)
        clipboard.setPrimaryClip(clip)
        
        // Mark ingredients as sent
        val ingredientNames = ingredientsToSend.map { it.name.lowercase() }
        lifecycleScope.launch {
            val result = mealPlanRepository.markIngredientsSent(currentWeekStart, ingredientNames)
            result.onSuccess { response ->
                weekPlan = weekPlan.copy(sentIngredients = response.sentIngredients.toSet())
                updateIngredientsCard()
            }
        }

        // Show dialog to open Gemini
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gemini_prompt_copied))
            .setMessage(getString(R.string.gemini_prompt_description))
            .setPositiveButton(getString(R.string.open_gemini)) { _, _ ->
                openGemini()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openGemini() {
        try {
            // Try to open Gemini app
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.gemini_not_found), Toast.LENGTH_LONG).show()
        }
    }
}
