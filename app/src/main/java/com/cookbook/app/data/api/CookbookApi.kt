package com.cookbook.app.data.api

import com.cookbook.app.data.models.CookbookCollection
import com.cookbook.app.data.models.GoogleLoginRequest
import com.cookbook.app.data.models.ImportedRecipeData
import com.cookbook.app.data.models.LoginRequest
import com.cookbook.app.data.models.LoginResponse
import com.cookbook.app.data.models.ExcludeIngredientsRequest
import com.cookbook.app.data.models.ExcludeIngredientsResponse
import com.cookbook.app.data.models.MarkIngredientsSentRequest
import com.cookbook.app.data.models.MarkIngredientsSentResponse
import com.cookbook.app.data.models.MealPlanResponse
import com.cookbook.app.data.models.MealSlotUpdateRequest
import com.cookbook.app.data.models.PaginatedRecipes
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.models.RecipeRequest
import com.cookbook.app.data.models.User
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for Cookbook backend
 */
interface CookbookApi {
    
    // ==================== Auth ====================
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<LoginResponse>
    
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<User>
    
    // ==================== Recipes ====================
    
    @GET("recipes")
    suspend fun getRecipes(
        @Query("category") category: String? = null,
        @Query("collections") collections: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedRecipes>
    
    @GET("recipes/{id}")
    suspend fun getRecipe(@Path("id") id: String): Response<Recipe>
    
    @POST("recipes")
    suspend fun createRecipe(@Body recipe: RecipeRequest): Response<Recipe>
    
    @PUT("recipes/{id}")
    suspend fun updateRecipe(
        @Path("id") id: String,
        @Body recipe: RecipeRequest
    ): Response<Recipe>
    
    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String): Response<Unit>
    
    // ==================== Categories ====================
    
    @GET("categories")
    suspend fun getCategories(): Response<List<String>>
    
    // ==================== Collections ====================
    
    @GET("collections")
    suspend fun getCollections(): Response<List<CookbookCollection>>
    
    @GET("collections/{id}")
    suspend fun getCollection(@Path("id") id: String): Response<CookbookCollection>
    
    @POST("collections/{collectionId}/recipes/{recipeId}")
    suspend fun addRecipeToCollection(
        @Path("collectionId") collectionId: String,
        @Path("recipeId") recipeId: String
    ): Response<Unit>
    
    @DELETE("collections/{collectionId}/recipes/{recipeId}")
    suspend fun removeRecipeFromCollection(
        @Path("collectionId") collectionId: String,
        @Path("recipeId") recipeId: String
    ): Response<Unit>
    
    // ==================== Import ====================
    
    @POST("import")
    suspend fun importRecipe(@Body request: ImportRequest): Response<ImportedRecipeData>
    
    // ==================== Meal Plans (Weekly Planner) ====================
    
    @GET("mealplans/{weekStart}")
    suspend fun getMealPlan(@Path("weekStart") weekStart: String): Response<MealPlanResponse>
    
    @PATCH("mealplans/{weekStart}/slot")
    suspend fun updateMealSlot(
        @Path("weekStart") weekStart: String,
        @Body request: MealSlotUpdateRequest
    ): Response<MealPlanResponse>
    
    @POST("mealplans/{weekStart}/sent-ingredients")
    suspend fun markIngredientsSent(
        @Path("weekStart") weekStart: String,
        @Body request: MarkIngredientsSentRequest
    ): Response<MarkIngredientsSentResponse>
    
    @DELETE("mealplans/{weekStart}/sent-ingredients")
    suspend fun resetSentIngredients(
        @Path("weekStart") weekStart: String
    ): Response<Unit>
    
    @POST("mealplans/{weekStart}/excluded-ingredients")
    suspend fun excludeIngredients(
        @Path("weekStart") weekStart: String,
        @Body request: ExcludeIngredientsRequest
    ): Response<ExcludeIngredientsResponse>
    
    @DELETE("mealplans/{weekStart}/excluded-ingredients/{ingredientName}")
    suspend fun restoreIngredient(
        @Path("weekStart") weekStart: String,
        @Path("ingredientName") ingredientName: String
    ): Response<ExcludeIngredientsResponse>
    
    @DELETE("mealplans/{weekStart}/excluded-ingredients")
    suspend fun resetExcludedIngredients(
        @Path("weekStart") weekStart: String
    ): Response<Unit>
    
    @DELETE("mealplans/{weekStart}")
    suspend fun deleteMealPlan(@Path("weekStart") weekStart: String): Response<Unit>
}

/**
 * Request model for recipe import
 */
data class ImportRequest(
    val url: String
)
