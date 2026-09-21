package com.cookbook.app.data.repository

import android.util.Log
import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.api.ImportRequest
import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.CookedRequest
import com.cookbook.app.data.models.CookedResponse
import com.cookbook.app.data.models.FavoriteResponse
import com.cookbook.app.data.models.FeaturedRecipe
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

        /**
         * Längster Suchbegriff, den das Backend annimmt (MAX_SEARCH_LENGTH in
         * backend/src/lib/recipe-search.ts). Längere Eingaben beantwortet es mit
         * HTTP 400 — hier abgefangen, damit die Meldung verständlich bleibt.
         */
        const val MAX_SEARCH_LENGTH = 200
    }

    /**
     * Get paginated recipes with optional filters
     *
     * @param collectionIds List of collection IDs to filter by (optional, can select multiple)
     * @param search Volltextsuche des Backends über Titel, Kategorien, Zutaten,
     *   Notizen und Zubereitung. Die Suche läuft serverseitig und umfasst damit
     *   auch Rezepte, die noch nicht geladen wurden.
     */
    suspend fun getRecipes(
        category: String? = null,
        collectionIds: List<String>? = null,
        search: String? = null,
        favoritesOnly: Boolean = false,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<PaginatedRecipes> {
        if (search != null && search.length > MAX_SEARCH_LENGTH) {
            return Result.failure(
                Exception("Suchbegriff darf höchstens $MAX_SEARCH_LENGTH Zeichen lang sein")
            )
        }

        return try {
            // Convert list of collection IDs to comma-separated string
            val collectionsParam = collectionIds?.takeIf { it.isNotEmpty() }?.joinToString(",")
            Log.d(TAG, "getRecipes called: category=$category, collections=$collectionsParam, search=$search, limit=$limit, offset=$offset")
            val response = api.getRecipes(
                category = category,
                collections = collectionsParam,
                search = search,
                favorite = true.takeIf { favoritesOnly },
                sort = sort,
                limit = limit,
                offset = offset
            )
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

    // ==================== Kochhistorie & Favoriten ====================
    //
    // Beides lebt auf dem Server. Die Aufrufe werden bewusst nicht lokal
    // gepuffert — schlägt einer fehl, rollt der aufrufende Screen die
    // optimistische Anzeige zurück und meldet es.

    /**
     * Zählt einen Kochvorgang. Wird vom "Fertig"-Button im Kochmodus gerufen.
     */
    suspend fun markCooked(recipeId: String, servings: Int? = null): Result<CookedResponse> {
        return try {
            val response = api.markCooked(recipeId, CookedRequest(servings))
            if (response.isSuccessful) {
                Result.success(response.body() ?: CookedResponse())
            } else {
                Log.e(TAG, "markCooked failed: code=${response.code()}")
                Result.failure(Exception("Kochvorgang konnte nicht gespeichert werden"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "markCooked exception", e)
            Result.failure(e)
        }
    }

    /**
     * Nimmt den letzten Kochvorgang zurück.
     */
    suspend fun undoLastCooked(recipeId: String): Result<CookedResponse> {
        return try {
            val response = api.undoLastCooked(recipeId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: CookedResponse())
            } else {
                Result.failure(Exception("Kochvorgang konnte nicht zurückgenommen werden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Setzt oder entfernt das Herz. Idempotent auf beiden Seiten.
     */
    suspend fun setFavorite(recipeId: String, isFavorite: Boolean): Result<FavoriteResponse> {
        return try {
            val response = if (isFavorite) {
                api.addFavorite(recipeId, emptyMap())
            } else {
                api.removeFavorite(recipeId)
            }
            if (response.isSuccessful) {
                Result.success(response.body() ?: FavoriteResponse(isFavorite))
            } else {
                Log.e(TAG, "setFavorite failed: code=${response.code()}")
                Result.failure(Exception("Favorit konnte nicht gespeichert werden"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "setFavorite exception", e)
            Result.failure(e)
        }
    }

    /**
     * Rezept der Woche. Liefert das Backend den Endpunkt noch nicht, scheitert
     * der Aufruf — der Aufmacher fällt dann auf das neueste Rezept zurück.
     */
    suspend fun getFeaturedRecipe(): Result<FeaturedRecipe> {
        return try {
            val response = api.getFeaturedRecipe()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Rezept der Woche nicht verfügbar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
