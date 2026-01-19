package com.cookbook.app.data.repository

import android.util.Log
import com.cookbook.app.data.api.ApiClient
import com.cookbook.app.data.models.*
import java.util.*

/**
 * Repository for meal plan operations
 */
class MealPlanRepository {
    
    companion object {
        private const val TAG = "MealPlanRepository"
    }
    
    private val api get() = ApiClient.getApi()
    
    /**
     * Get meal plan for a specific week
     */
    suspend fun getMealPlan(weekStart: Date): Result<MealPlanResponse> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "getMealPlan: weekStart=$weekStartStr")
            
            val response = api.getMealPlan(weekStartStr)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "getMealPlan success: ${response.body()?.meals?.size} meals")
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Laden des Wochenplans"
                Log.e(TAG, "getMealPlan error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMealPlan exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update a single meal slot
     */
    suspend fun updateMealSlot(
        weekStart: Date,
        dayIndex: Int,
        mealType: MealType,
        recipeId: String?,
        servings: Int
    ): Result<MealPlanResponse> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "updateMealSlot: weekStart=$weekStartStr, day=$dayIndex, type=${mealType.key}, recipe=$recipeId")
            
            val request = MealSlotUpdateRequest(
                dayIndex = dayIndex,
                mealType = mealType.key,
                recipeId = recipeId,
                servings = servings
            )
            
            val response = api.updateMealSlot(weekStartStr, request)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "updateMealSlot success")
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Speichern der Mahlzeit"
                Log.e(TAG, "updateMealSlot error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateMealSlot exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Mark ingredients as sent to Gemini
     */
    suspend fun markIngredientsSent(
        weekStart: Date,
        ingredients: List<String>
    ): Result<MarkIngredientsSentResponse> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "markIngredientsSent: weekStart=$weekStartStr, count=${ingredients.size}")
            
            val request = MarkIngredientsSentRequest(ingredients)
            val response = api.markIngredientsSent(weekStartStr, request)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "markIngredientsSent success")
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Markieren der Zutaten"
                Log.e(TAG, "markIngredientsSent error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "markIngredientsSent exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Reset sent ingredients for a week
     */
    suspend fun resetSentIngredients(weekStart: Date): Result<Unit> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "resetSentIngredients: weekStart=$weekStartStr")
            
            val response = api.resetSentIngredients(weekStartStr)
            
            if (response.isSuccessful) {
                Log.d(TAG, "resetSentIngredients success")
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Zurücksetzen der Zutaten"
                Log.e(TAG, "resetSentIngredients error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetSentIngredients exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete meal plan for a week
     */
    suspend fun deleteMealPlan(weekStart: Date): Result<Unit> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "deleteMealPlan: weekStart=$weekStartStr")
            
            val response = api.deleteMealPlan(weekStartStr)
            
            if (response.isSuccessful) {
                Log.d(TAG, "deleteMealPlan success")
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Löschen des Wochenplans"
                Log.e(TAG, "deleteMealPlan error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteMealPlan exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Exclude ingredients from shopping list
     */
    suspend fun excludeIngredients(
        weekStart: Date,
        ingredients: List<String>
    ): Result<ExcludeIngredientsResponse> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "excludeIngredients: weekStart=$weekStartStr, count=${ingredients.size}")
            
            val request = ExcludeIngredientsRequest(ingredients)
            val response = api.excludeIngredients(weekStartStr, request)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "excludeIngredients success")
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Ausschließen der Zutaten"
                Log.e(TAG, "excludeIngredients error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "excludeIngredients exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Restore a single excluded ingredient
     */
    suspend fun restoreIngredient(
        weekStart: Date,
        ingredientName: String
    ): Result<ExcludeIngredientsResponse> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "restoreIngredient: weekStart=$weekStartStr, ingredient=$ingredientName")
            
            val response = api.restoreIngredient(weekStartStr, ingredientName)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "restoreIngredient success")
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Wiederherstellen der Zutat"
                Log.e(TAG, "restoreIngredient error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreIngredient exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Reset all excluded ingredients for a week
     */
    suspend fun resetExcludedIngredients(weekStart: Date): Result<Unit> {
        return try {
            val weekStartStr = WeekPlan.formatDateForApi(weekStart)
            Log.d(TAG, "resetExcludedIngredients: weekStart=$weekStartStr")
            
            val response = api.resetExcludedIngredients(weekStartStr)
            
            if (response.isSuccessful) {
                Log.d(TAG, "resetExcludedIngredients success")
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Fehler beim Zurücksetzen der ausgeschlossenen Zutaten"
                Log.e(TAG, "resetExcludedIngredients error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetExcludedIngredients exception", e)
            Result.failure(e)
        }
    }
}
