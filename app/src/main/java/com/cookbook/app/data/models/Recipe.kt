package com.cookbook.app.data.models

import java.io.Serializable

/**
 * Recipe model matching the backend API response format
 *
 * The backend transforms the data before sending:
 * - categories: string[] (category names)
 * - collections: { id, name }[]
 * - ingredients: { name, amount }[]
 *
 * cookCount/lastCookedAt/isFavorite kommen aus der Kochhistorie bzw. den
 * Favoriten des angemeldeten Nutzers. Alle drei sind additiv: liefert das
 * Backend sie (noch) nicht, greifen die JVM-Defaults 0/null/false.
 */
data class Recipe(
    val id: String,
    val title: String,
    val images: List<String> = emptyList(),
    val instructions: String = "",
    val notes: String? = null,
    val prepTime: Int = 0,
    val restTime: Int = 0,
    val cookTime: Int = 0,
    val totalTime: Int = 0,
    val servings: Int = 4,
    val caloriesPerUnit: Int = 0,
    val weightUnit: String = "",
    val sourceUrl: String? = null,
    val createdAt: String = "",
    val userId: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val categories: List<String> = emptyList(),  // Backend returns string array!
    val collections: List<RecipeCollection> = emptyList(),
    val cookCount: Int = 0,
    val lastCookedAt: String? = null,
    val isFavorite: Boolean = false
) : Serializable {

    val firstImage: String?
        get() = images.firstOrNull()

    // For compatibility - categories is already a string list
    val categoryNames: List<String>
        get() = categories

    /** Name der Sammlung, die Marker und Badge einfärbt; sonst die erste Kategorie. */
    val collectionLabel: String?
        get() = collections.firstOrNull()?.name ?: categories.firstOrNull()

    /** Arbeitszeit = Vorbereitung + Kochen (Ruhezeit zählt nicht als Arbeit). */
    val activeTime: Int
        get() = prepTime + cookTime
}

/**
 * Ingredient model (simplified - backend only sends name and amount)
 */
data class Ingredient(
    val name: String,
    val amount: String
) : Serializable

/**
 * Collection reference in recipe (simplified - backend only sends id and name)
 */
data class RecipeCollection(
    val id: String,
    val name: String
) : Serializable

/**
 * Full Collection model for collection list endpoint
 * Includes recipe previews from the backend
 */
data class CookbookCollection(
    val id: String = "",
    val name: String,
    val description: String? = null,
    val createdAt: String = "",
    val recipeCount: Int = 0,
    val recipes: List<RecipePreview> = emptyList()
) : Serializable {
    val firstRecipeImage: String?
        get() = recipes.firstOrNull()?.images?.firstOrNull()
}

/**
 * Simple recipe preview for collection list
 */
data class RecipePreview(
    val id: String,
    val title: String,
    val images: List<String> = emptyList()
) : Serializable

/**
 * Request model for creating/updating recipes
 */
data class RecipeRequest(
    val title: String,
    val images: List<String> = emptyList(),
    val instructions: String = "",
    val notes: String? = null,
    val prepTime: Int = 0,
    val restTime: Int = 0,
    val cookTime: Int = 0,
    val totalTime: Int = 0,
    val servings: Int = 4,
    val caloriesPerUnit: Int = 0,
    val weightUnit: String = "",
    val sourceUrl: String? = null,
    val ingredients: List<IngredientRequest> = emptyList(),
    val categories: List<String> = emptyList()
)

/**
 * Request model for ingredients
 */
data class IngredientRequest(
    val name: String,
    val amount: String
) : Serializable

/**
 * Response model for imported recipe data
 */
data class ImportedRecipeData(
    val title: String,
    val images: List<String> = emptyList(),
    val ingredients: List<IngredientRequest> = emptyList(),
    val instructions: String = "",
    val prepTime: Int = 0,
    val restTime: Int = 0,
    val cookTime: Int = 0,
    val totalTime: Int = 0,
    val servings: Int = 4,
    val caloriesPerUnit: Int = 0,
    val weightUnit: String = "",
    val categories: List<String> = emptyList(),
    val sourceUrl: String = ""
) : Serializable

/**
 * Recipe list item (for paginated list view with thumbnail)
 *
 * collections ist nullable: ältere Backend-Stände liefern das Feld in der
 * Listenantwort nicht mit.
 */
data class RecipeListItem(
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val prepTime: Int = 0,
    val cookTime: Int = 0,
    val totalTime: Int = 0,
    val servings: Int = 4,
    val categories: List<String> = emptyList(),
    val collections: List<RecipeCollection>? = null,
    val createdAt: String = "",
    val cookCount: Int = 0,
    val lastCookedAt: String? = null,
    val isFavorite: Boolean = false
) : Serializable {

    /** Name der Sammlung, die Marker und Badge einfärbt; sonst die erste Kategorie. */
    val collectionLabel: String?
        get() = collections?.firstOrNull()?.name ?: categories.firstOrNull()

    /** Sachzeile der Listenkarte, z. B. "Hokkaido · Kokosmilch". */
    val subtitle: String
        get() = categories.joinToString(" · ")
}

/**
 * Paginated response for recipe list
 */
data class PaginatedRecipes(
    val items: List<RecipeListItem>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)
