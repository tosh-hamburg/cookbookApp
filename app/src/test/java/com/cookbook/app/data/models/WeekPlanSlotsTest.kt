package com.cookbook.app.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Date

/**
 * Mehrere Gerichte je Slot (Issue #4): Einlesen der Antwort, Grenzen und die
 * unveränderlichen Änderungen am Wochenplan.
 */
class WeekPlanSlotsTest {

    private val emptyPlan = WeekPlan.createEmpty(Date(0L))

    private fun recipe(id: String, servings: Int = 2) = MealRecipeResponse(
        id = id,
        title = "Rezept $id",
        servings = servings
    )

    private fun dish(id: String, servings: Int = 2) = PlannedDish(recipe(id), servings)

    private fun slotResponse(
        dayIndex: Int,
        mealType: String,
        position: Int,
        recipeId: String,
        servings: Int = 2
    ) = MealSlotResponse(
        dayIndex = dayIndex,
        mealType = mealType,
        position = position,
        servings = servings,
        recipe = recipe(recipeId)
    )

    @Test
    fun `liest mehrere Gerichte eines Slots in der Reihenfolge der Position`() {
        val response = MealPlanResponse(
            id = "plan",
            weekStart = "1970-01-01",
            meals = listOf(
                slotResponse(0, "dinner", position = 1, recipeId = "nachtisch"),
                slotResponse(0, "dinner", position = 0, recipeId = "hauptgericht", servings = 4)
            )
        )

        val plan = emptyPlan.updateFromResponse(response)
        val dishes = plan.dishesAt(0, MealType.DINNER)

        assertEquals(listOf("hauptgericht", "nachtisch"), dishes.map { it.recipe.id })
        assertEquals(4, dishes.first().servings)
    }

    @Test
    fun `zaehlt belegte Slots einmal und Gerichte einzeln`() {
        val response = MealPlanResponse(
            id = "plan",
            weekStart = "1970-01-01",
            meals = listOf(
                slotResponse(0, "dinner", position = 0, recipeId = "hauptgericht"),
                slotResponse(0, "dinner", position = 1, recipeId = "nachtisch"),
                slotResponse(1, "lunch", position = 0, recipeId = "suppe")
            )
        )

        val plan = emptyPlan.updateFromResponse(response)

        assertEquals(2, plan.getFilledSlotCount())
        assertEquals(3, plan.getTotalDishCount())
    }

    @Test
    fun `ignoriert Zeilen ohne Rezept, mit unbekannter Mahlzeit oder ausserhalb der Woche`() {
        val response = MealPlanResponse(
            id = "plan",
            weekStart = "1970-01-01",
            meals = listOf(
                MealSlotResponse(dayIndex = 0, mealType = "dinner", position = 0, servings = 2, recipe = null),
                slotResponse(0, "snack", position = 0, recipeId = "zwischendurch"),
                slotResponse(7, "dinner", position = 0, recipeId = "achter-tag")
            )
        )

        val plan = emptyPlan.updateFromResponse(response)

        assertEquals(0, plan.getTotalDishCount())
    }

    @Test
    fun `haengt ein Gericht an und laesst den bisherigen Plan unberuehrt`() {
        val withMain = emptyPlan.withDishAdded(0, MealType.DINNER, dish("hauptgericht"))
        val withDessert = withMain.withDishAdded(0, MealType.DINNER, dish("nachtisch"))

        assertEquals(listOf("hauptgericht"), withMain.dishesAt(0, MealType.DINNER).map { it.recipe.id })
        assertEquals(
            listOf("hauptgericht", "nachtisch"),
            withDessert.dishesAt(0, MealType.DINNER).map { it.recipe.id }
        )
        assertEquals(0, emptyPlan.getTotalDishCount())
    }

    @Test
    fun `andere Tage und Slots behalten ihre Gerichte`() {
        val plan = emptyPlan
            .withDishAdded(0, MealType.DINNER, dish("hauptgericht"))
            .withDishAdded(1, MealType.LUNCH, dish("suppe"))
            .withDishAdded(0, MealType.BREAKFAST, dish("muesli"))

        assertEquals(listOf("suppe"), plan.dishesAt(1, MealType.LUNCH).map { it.recipe.id })
        assertEquals(listOf("muesli"), plan.dishesAt(0, MealType.BREAKFAST).map { it.recipe.id })
        assertEquals(listOf("hauptgericht"), plan.dishesAt(0, MealType.DINNER).map { it.recipe.id })
    }

    @Test
    fun `lehnt dasselbe Rezept zweimal im Slot ab`() {
        val plan = emptyPlan.withDishAdded(0, MealType.DINNER, dish("hauptgericht"))

        assertEquals(AddDishCheck.DUPLICATE, plan.canAddDish(0, MealType.DINNER, "hauptgericht"))
        assertSame(plan, plan.withDishAdded(0, MealType.DINNER, dish("hauptgericht")))
        assertEquals(AddDishCheck.OK, plan.canAddDish(0, MealType.LUNCH, "hauptgericht"))
    }

    @Test
    fun `lehnt ein Gericht ueber der Hoechstzahl ab`() {
        val fullSlot = (0 until MAX_DISHES_PER_SLOT).fold(emptyPlan) { plan, index ->
            plan.withDishAdded(0, MealType.DINNER, dish("gericht-$index"))
        }

        assertEquals(MAX_DISHES_PER_SLOT, fullSlot.dishesAt(0, MealType.DINNER).size)
        assertEquals(AddDishCheck.FULL, fullSlot.canAddDish(0, MealType.DINNER, "eins-zuviel"))
        assertSame(fullSlot, fullSlot.withDishAdded(0, MealType.DINNER, dish("eins-zuviel")))
    }

    @Test
    fun `entfernt genau ein Gericht und behaelt die uebrigen`() {
        val plan = emptyPlan
            .withDishAdded(0, MealType.DINNER, dish("hauptgericht"))
            .withDishAdded(0, MealType.DINNER, dish("nachtisch"))

        val afterRemove = plan.withDishRemoved(0, MealType.DINNER, 0)

        assertEquals(listOf("nachtisch"), afterRemove.dishesAt(0, MealType.DINNER).map { it.recipe.id })
        assertSame(plan, plan.withDishRemoved(0, MealType.DINNER, 5))
    }

    @Test
    fun `setzt Portionen je Gericht und begrenzt sie auf eins bis 99`() {
        val plan = emptyPlan
            .withDishAdded(0, MealType.DINNER, dish("hauptgericht", servings = 4))
            .withDishAdded(0, MealType.DINNER, dish("nachtisch", servings = 2))

        val changed = plan
            .withDishServings(0, MealType.DINNER, 1, 6)
            .withDishServings(0, MealType.DINNER, 0, 0)

        assertEquals(listOf(MIN_SERVINGS, 6), changed.dishesAt(0, MealType.DINNER).map { it.servings })
        assertEquals(
            MAX_SERVINGS,
            plan.withDishServings(0, MealType.DINNER, 0, 500).dishesAt(0, MealType.DINNER).first().servings
        )
    }

    @Test
    fun `leert einen Slot ueber eine leere Gerichteliste`() {
        val plan = emptyPlan.withDishAdded(0, MealType.DINNER, dish("hauptgericht"))

        val cleared = plan.withDishesAt(0, MealType.DINNER, emptyList())

        assertEquals(emptyList<PlannedDish>(), cleared.dishesAt(0, MealType.DINNER))
        assertEquals(0, cleared.getFilledSlotCount())
    }

    @Test
    fun `ignoriert Tage ausserhalb der Woche`() {
        assertSame(emptyPlan, emptyPlan.withDishesAt(7, MealType.DINNER, listOf(dish("achter-tag"))))
        assertEquals(emptyList<PlannedDish>(), emptyPlan.dishesAt(-1, MealType.DINNER))
    }
}
