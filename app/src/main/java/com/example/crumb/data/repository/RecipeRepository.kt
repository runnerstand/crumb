package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.RecipeCreateRequest
import com.example.crumb.data.remote.model.RecipeIngredientRequest
import com.example.crumb.data.remote.model.RecipeResponse

class RecipeRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getRecipes(): List<RecipeResponse> {
        return apiService.getRecipes()
    }

    suspend fun getRecipe(recipeId: Int): RecipeResponse {
        return apiService.getRecipe(recipeId)
    }

    suspend fun createRecipe(
        title: String,
        ingredients: List<RecipeIngredientRequest>,
        instructions: List<String>,
        cookingTimeMinutes: Int?
    ): RecipeResponse {
        return apiService.createRecipe(
            RecipeCreateRequest(
                title = title,
                ingredients = ingredients,
                instructions = instructions,
                cookingTimeMinutes = cookingTimeMinutes
            )
        )
    }

    suspend fun updateRecipe(
        recipeId: Int,
        title: String,
        ingredients: List<RecipeIngredientRequest>,
        instructions: List<String>,
        cookingTimeMinutes: Int?
    ): RecipeResponse {
        return apiService.updateRecipe(
            recipeId = recipeId,
            request = RecipeCreateRequest(
                title = title,
                ingredients = ingredients,
                instructions = instructions,
                cookingTimeMinutes = cookingTimeMinutes
            )
        )
    }

    suspend fun deleteRecipe(recipeId: Int) {
        apiService.deleteRecipe(recipeId)
    }
}
