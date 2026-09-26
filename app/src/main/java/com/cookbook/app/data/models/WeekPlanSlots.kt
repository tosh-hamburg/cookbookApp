package com.cookbook.app.data.models

/**
 * Gerichte in den Slots eines Wochenplans lesen und ändern.
 *
 * Ein Slot (Tag × Mahlzeit) kann mehrere Gerichte aufnehmen, zum Beispiel
 * Hauptgericht und Nachtisch. Alle Funktionen geben einen neuen Plan zurück,
 * der übergebene bleibt unverändert.
 */

/** Muss zu MAX_DISHES_PER_SLOT im Backend (lib/meal-slots.ts) passen. */
const val MAX_DISHES_PER_SLOT = 6

const val DEFAULT_SERVINGS = 2
const val MIN_SERVINGS = 1
const val MAX_SERVINGS = 99

/** Ob ein Gericht noch in einen Slot passt — und wenn nicht, warum. */
enum class AddDishCheck { OK, DUPLICATE, FULL }

/** Gerichte eines Slots in Reihenfolge; leer, wenn der Tag nicht existiert. */
fun WeekPlan.dishesAt(dayIndex: Int, mealType: MealType): List<PlannedDish> =
    days.getOrNull(dayIndex)?.getMeal(mealType)?.dishes ?: emptyList()

/** Gerichte eines Slots ersetzen; alle anderen Tage bleiben unverändert. */
fun WeekPlan.withDishesAt(dayIndex: Int, mealType: MealType, dishes: List<PlannedDish>): WeekPlan {
    if (dayIndex !in days.indices) return this
    return copy(
        days = days.mapIndexed { index, day ->
            if (index == dayIndex) day.withMeal(MealSlot(mealType, dishes)) else day
        }
    )
}

fun WeekPlan.canAddDish(dayIndex: Int, mealType: MealType, recipeId: String): AddDishCheck {
    val dishes = dishesAt(dayIndex, mealType)
    return when {
        dishes.any { it.recipe.id == recipeId } -> AddDishCheck.DUPLICATE
        dishes.size >= MAX_DISHES_PER_SLOT -> AddDishCheck.FULL
        else -> AddDishCheck.OK
    }
}

/** Gericht ans Ende des Slots hängen; bei Duplikat oder vollem Slot unverändert. */
fun WeekPlan.withDishAdded(dayIndex: Int, mealType: MealType, dish: PlannedDish): WeekPlan {
    if (canAddDish(dayIndex, mealType, dish.recipe.id) != AddDishCheck.OK) return this
    return withDishesAt(dayIndex, mealType, dishesAt(dayIndex, mealType) + dish)
}

fun WeekPlan.withDishRemoved(dayIndex: Int, mealType: MealType, dishIndex: Int): WeekPlan {
    val dishes = dishesAt(dayIndex, mealType)
    if (dishIndex !in dishes.indices) return this
    return withDishesAt(dayIndex, mealType, dishes.filterIndexed { index, _ -> index != dishIndex })
}

/** Portionen eines Gerichts setzen; der Wert wird auf 1–99 begrenzt. */
fun WeekPlan.withDishServings(
    dayIndex: Int,
    mealType: MealType,
    dishIndex: Int,
    servings: Int
): WeekPlan {
    val dishes = dishesAt(dayIndex, mealType)
    if (dishIndex !in dishes.indices) return this
    val clamped = servings.coerceIn(MIN_SERVINGS, MAX_SERVINGS)
    return withDishesAt(
        dayIndex,
        mealType,
        dishes.mapIndexed { index, dish ->
            if (index == dishIndex) dish.copy(servings = clamped) else dish
        }
    )
}
