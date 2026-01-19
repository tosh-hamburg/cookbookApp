package com.cookbook.app.data.models

import java.io.Serializable

/**
 * Recipe model matching the backend API response format
 * 
 * The backend transforms the data before sending:
 * - categories: string[] (category names)
 * - collections: { id, name }[]
 * - ingredients: { name, amount }[]
 */
data class Recipe(
    val id: String,
    val title: String,
    val images: List<String> = emptyList(),
    val instructions: String = "",
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
    val collections: List<RecipeCollection> = emptyList()
) : Serializable {
    
    val firstImage: String?
        get() = images.firstOrNull()
    
    // For compatibility - categories is already a string list
    val categoryNames: List<String>
        get() = categories
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
    val createdAt: String = ""
) : Serializable

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
