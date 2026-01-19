package com.cookbook.app.data.repository

import android.util.Log
import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.api.ImportRequest
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.ImportedRecipeData
import com.cookbook.app.data.models.PaginatedRecipes
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.models.RecipeRequest

/**
 * Repository for recipe operations
 */
class RecipeRepository {
    
    private val api by lazy { ApiClient.getApi() }
    
    companion object {
        private const val TAG = "RecipeRepository"
    }
    
    /**
     * Get paginated recipes with optional filters
     */
    suspend fun getRecipes(
        category: String? = null,
        collection: String? = null,
        search: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<PaginatedRecipes> {
        return try {
            Log.d(TAG, "getRecipes called: category=$category, collection=$collection, search=$search, limit=$limit, offset=$offset")
            val response = api.getRecipes(category, collection, search, limit, offset)
            Log.d(TAG, "getRecipes response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val paginatedRecipes = response.body()!!
                Log.d(TAG, "getRecipes success: ${paginatedRecipes.items.size} recipes loaded, total=${paginatedRecipes.total}, hasMore=${paginatedRecipes.hasMore}")
                paginatedRecipes.items.take(3).forEach { recipe ->
                    Log.d(TAG, "  Recipe: id=${recipe.id}, title=${recipe.title}")
                }
                Result.success(paginatedRecipes)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getRecipes failed: code=${response.code()}, error=$errorBody")
                Result.failure(Exception("Rezepte konnten nicht geladen werden: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecipes exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get a single recipe by ID
     */
    suspend fun getRecipe(id: String): Result<Recipe> {
        return try {
            val response = api.getRecipe(id)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Rezept konnte nicht geladen werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a new recipe
     */
    suspend fun createRecipe(recipe: RecipeRequest): Result<Recipe> {
        return try {
            val response = api.createRecipe(recipe)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Rezept konnte nicht erstellt werden"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing recipe
     */
    suspend fun updateRecipe(id: String, recipe: RecipeRequest): Result<Recipe> {
        return try {
            Log.d(TAG, "updateRecipe: id=$id")
            Log.d(TAG, "updateRecipe: sending ${recipe.images.size} images")
            recipe.images.forEachIndexed { index, img ->
                Log.d(TAG, "  Image $index: ${img.length} chars, starts with: ${img.take(50)}")
            }
            
            val response = api.updateRecipe(id, recipe)
            Log.d(TAG, "updateRecipe response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val savedRecipe = response.body()!!
                Log.d(TAG, "updateRecipe: received ${savedRecipe.images.size} images back")
                savedRecipe.images.forEachIndexed { index, img ->
                    Log.d(TAG, "  Received Image $index: ${img.length} chars")
                }
                Result.success(savedRecipe)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Rezept konnte nicht aktualisiert werden"
                Log.e(TAG, "updateRecipe failed: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateRecipe exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a recipe
     */
    suspend fun deleteRecipe(id: String): Result<Unit> {
        return try {
            val response = api.deleteRecipe(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Rezept konnte nicht gelöscht werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Import recipe from URL
     */
    suspend fun importRecipe(url: String): Result<ImportedRecipeData> {
        return try {
            val response = api.importRecipe(ImportRequest(url))
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Rezept konnte nicht importiert werden"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all categories
     */
    suspend fun getCategories(): Result<List<String>> {
        return try {
            val response = api.getCategories()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Kategorien konnten nicht geladen werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all collections
     */
    suspend fun getCollections(): Result<List<CookbookCollection>> {
        return try {
            val response = api.getCollections()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Sammlungen konnten nicht geladen werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add recipe to collection
     */
    suspend fun addRecipeToCollection(collectionId: String, recipeId: String): Result<Unit> {
        return try {
            val response = api.addRecipeToCollection(collectionId, recipeId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Rezept konnte nicht zur Sammlung hinzugefügt werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove recipe from collection
     */
    suspend fun removeRecipeFromCollection(collectionId: String, recipeId: String): Result<Unit> {
        return try {
            val response = api.removeRecipeFromCollection(collectionId, recipeId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Rezept konnte nicht aus der Sammlung entfernt werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
