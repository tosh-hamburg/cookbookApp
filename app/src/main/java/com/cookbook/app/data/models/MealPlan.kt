package com.cookbook.app.data.models

import android.content.Context
import com.cookbook.app.R
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Meal type enum
 */
enum class MealType(val key: String, val labelResId: Int) {
    BREAKFAST("breakfast", R.string.breakfast),
    LUNCH("lunch", R.string.lunch),
    DINNER("dinner", R.string.dinner);
    
    /**
     * Get the localized label for this meal type
     */
    fun getLabel(context: Context): String = context.getString(labelResId)
    
    companion object {
        fun fromKey(key: String): MealType? = entries.find { it.key == key }
    }
}

/**
 * Helper object for localized day names
 */
object DayNames {
    fun getDayName(context: Context, dayIndex: Int): String {
        val dayNames = context.resources.getStringArray(R.array.day_names)
        return dayNames.getOrElse(dayIndex) { "" }
    }
    
    fun getDayNameShort(context: Context, dayIndex: Int): String {
        val dayNamesShort = context.resources.getStringArray(R.array.day_names_short)
        return dayNamesShort.getOrElse(dayIndex) { "" }
    }
}

/**
 * MealSlot from API response — eine Zeile je Gericht. Ein Slot (Tag × Mahlzeit)
 * kann mehrere Gerichte enthalten; [position] legt die Reihenfolge fest.
 */
data class MealSlotResponse(
    val dayIndex: Int,
    val mealType: String,
    val position: Int = 0,
    val servings: Int,
    val recipe: MealRecipeResponse?
) : Serializable

/**
 * Simplified recipe data for meal slots
 */
data class MealRecipeResponse(
    val id: String,
    val title: String,
    val images: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val servings: Int,
    val totalTime: Int = 0,
    val categories: List<String> = emptyList()
) : Serializable {
    val firstImage: String?
        get() = images.firstOrNull()
}

/**
 * MealPlan API response
 */
data class MealPlanResponse(
    val id: String?,
    val weekStart: String,
    val sentIngredients: List<String> = emptyList(),
    val excludedIngredients: List<String> = emptyList(),
    val meals: List<MealSlotResponse> = emptyList()
) : Serializable

/**
 * Ein geplantes Gericht mit eigener Portionszahl.
 */
data class PlannedDish(
    val recipe: MealRecipeResponse,
    val servings: Int = DEFAULT_SERVINGS
) : Serializable

/**
 * Ein Slot des Wochenplans (Tag × Mahlzeit). Er kann mehrere Gerichte
 * aufnehmen, zum Beispiel Hauptgericht und Nachtisch.
 */
data class MealSlot(
    val mealType: MealType,
    val dishes: List<PlannedDish> = emptyList()
) : Serializable

/**
 * Day plan with all meals for a day
 */
data class DayPlan(
    val date: Date,
    val dayIndex: Int,
    val meals: Map<MealType, MealSlot> = MealType.entries.associateWith { MealSlot(it) }
) : Serializable {

    fun getMeal(mealType: MealType): MealSlot = meals[mealType] ?: MealSlot(mealType)

    /** Tag mit ersetztem Slot; der bisherige Tag bleibt unverändert. */
    fun withMeal(slot: MealSlot): DayPlan = copy(meals = meals + (slot.mealType to slot))

    fun getFormattedDate(): String {
        val format = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return format.format(date)
    }
    
    fun getDayName(context: Context): String = DayNames.getDayName(context, dayIndex)
    fun getDayNameShort(context: Context): String = DayNames.getDayNameShort(context, dayIndex)
}

/**
 * Complete week plan
 */
data class WeekPlan(
    val weekStart: Date,
    val weekEnd: Date,
    val days: List<DayPlan>,
    val sentIngredients: Set<String> = emptySet(),
    val excludedIngredients: Set<String> = emptySet()
) : Serializable {
    
    companion object {
        /**
         * Create an empty week plan starting from the given date
         */
        fun createEmpty(weekStart: Date): WeekPlan {
            val calendar = Calendar.getInstance().apply { time = weekStart }
            val days = (0 until 7).map { dayIndex ->
                val dayDate = calendar.time
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                DayPlan(date = dayDate, dayIndex = dayIndex)
            }
            
            val weekEnd = Calendar.getInstance().apply {
                time = weekStart
                add(Calendar.DAY_OF_MONTH, 6)
            }.time
            
            return WeekPlan(weekStart, weekEnd, days)
        }
        
        // Use Europe/Berlin timezone to match web frontend behavior
        // The web app was likely used in this timezone when creating meal plans
        private val webTimezone = TimeZone.getTimeZone("Europe/Berlin")
        
        /**
         * Get the start of the current week (Monday)
         * Uses Europe/Berlin timezone to match web frontend behavior.
         * This ensures consistency regardless of the device's local timezone.
         */
        fun getCurrentWeekStart(): Date {
            val calendar = Calendar.getInstance(webTimezone)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Calculate days since Monday (Monday = 2 in Calendar)
            val daysSinceMonday = when (dayOfWeek) {
                Calendar.SUNDAY -> 6
                else -> dayOfWeek - Calendar.MONDAY
            }
            
            calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            return calendar.time
        }
        
        /**
         * Get the start of the next week (next Monday)
         * Uses Europe/Berlin timezone to match web frontend behavior.
         */
        fun getNextWeekStart(): Date {
            val calendar = Calendar.getInstance(webTimezone)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Calculate days until next Monday
            val daysUntilMonday = when (dayOfWeek) {
                Calendar.SUNDAY -> 1
                else -> 8 - dayOfWeek + Calendar.MONDAY - 1
            }
            
            calendar.add(Calendar.DAY_OF_MONTH, daysUntilMonday)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            return calendar.time
        }
        
        /**
         * Format week range for display
         */
        fun formatWeekRange(weekStart: Date, weekEnd: Date): String {
            val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
            return "${format.format(weekStart)} - ${format.format(weekEnd)}"
        }
        
        /**
         * Get ISO week number
         */
        fun getWeekNumber(date: Date): Int {
            val calendar = Calendar.getInstance().apply { 
                time = date
                firstDayOfWeek = Calendar.MONDAY
            }
            return calendar.get(Calendar.WEEK_OF_YEAR)
        }
        
        /**
         * Format date for API (ISO 8601)
         * Uses UTC timezone to match the web frontend behavior.
         * The web app uses: weekStart.toISOString().split('T')[0]
         * which converts to UTC before formatting.
         */
        fun formatDateForApi(date: Date): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(date)
        }
    }
    
    /**
     * Convert API response to WeekPlan. Mehrere Zeilen desselben Slots werden
     * nach [MealSlotResponse.position] zu einer Gerichteliste zusammengefasst.
     */
    fun updateFromResponse(response: MealPlanResponse): WeekPlan {
        val dishesBySlot = response.meals
            .mapNotNull { slot ->
                val recipe = slot.recipe ?: return@mapNotNull null
                val mealType = MealType.fromKey(slot.mealType) ?: return@mapNotNull null
                if (slot.dayIndex !in days.indices) return@mapNotNull null
                SlotEntry(slot.dayIndex, mealType, slot.position, PlannedDish(recipe, slot.servings))
            }
            .sortedBy { it.position }
            .groupBy({ it.dayIndex to it.mealType }, { it.dish })

        val updatedDays = days.map { day ->
            day.copy(
                meals = MealType.entries.associateWith { mealType ->
                    MealSlot(mealType, dishesBySlot[day.dayIndex to mealType] ?: emptyList())
                }
            )
        }

        return copy(
            days = updatedDays,
            sentIngredients = response.sentIngredients.toSet(),
            excludedIngredients = response.excludedIngredients.toSet()
        )
    }

    /**
     * Belegte Slots (höchstens 21) — ein Slot zählt einmal, auch wenn Haupt-
     * gericht und Nachtisch darin liegen.
     */
    fun getFilledSlotCount(): Int {
        return days.sumOf { day ->
            day.meals.values.count { it.dishes.isNotEmpty() }
        }
    }

    /**
     * Alle geplanten Gerichte der Woche.
     */
    fun getTotalDishCount(): Int {
        return days.sumOf { day ->
            day.meals.values.sumOf { it.dishes.size }
        }
    }

    /**
     * Get all recipes in the week plan
     */
    fun getAllRecipes(): List<MealRecipeResponse> {
        return days.flatMap { day ->
            day.meals.values.flatMap { slot -> slot.dishes.map { it.recipe } }
        }.distinctBy { it.id }
    }
    
    fun getFormattedRange(): String = formatWeekRange(weekStart, weekEnd)
    fun getWeekNumber(): Int = Companion.getWeekNumber(weekStart)
}

/**
 * Aggregated ingredient for shopping list
 */
data class AggregatedIngredient(
    val name: String,
    val totalAmount: String,
    val sources: List<IngredientSource>
) : Serializable

/**
 * Source of an ingredient (which recipe and servings)
 */
data class IngredientSource(
    val recipeTitle: String,
    val servings: Int,
    val originalAmount: String
) : Serializable

/**
 * Eine Antwortzeile beim Einlesen: Gericht samt Slot und Reihenfolge.
 */
private data class SlotEntry(
    val dayIndex: Int,
    val mealType: MealType,
    val position: Int,
    val dish: PlannedDish
)

/**
 * Ein Gericht in einem Slot-Schreibzugriff.
 */
data class MealDishRequest(
    val recipeId: String,
    val servings: Int
)

/**
 * Gerichte eines Slots ersetzen; eine leere Liste leert den Slot.
 */
data class SlotDishesRequest(
    val dayIndex: Int,
    val mealType: String,
    val dishes: List<MealDishRequest>
)

/**
 * Request model for replacing whole slots (PUT .../slots)
 */
data class ReplaceSlotsRequest(
    val slots: List<SlotDishesRequest>
)

/**
 * Request model for appending one dish to a slot (POST .../slot)
 */
data class AppendDishRequest(
    val dayIndex: Int,
    val mealType: String,
    val recipeId: String,
    val servings: Int
)

/**
 * Request model for marking ingredients as sent
 */
data class MarkIngredientsSentRequest(
    val ingredients: List<String>
)

/**
 * Response model for marking ingredients as sent
 */
data class MarkIngredientsSentResponse(
    val sentIngredients: List<String>,
    val message: String
)

/**
 * Request model for excluding ingredients
 */
data class ExcludeIngredientsRequest(
    val ingredients: List<String>
)

/**
 * Response model for excluding ingredients
 */
data class ExcludeIngredientsResponse(
    val excludedIngredients: List<String>,
    val message: String
)
