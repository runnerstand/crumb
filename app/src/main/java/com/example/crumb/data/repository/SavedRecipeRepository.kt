package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.SavedRecipeCreateRequest
import com.example.crumb.data.remote.model.SavedRecipeResponse

class SavedRecipeRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getSavedRecipes(): List<SavedRecipeResponse> {
        return apiService.getSavedRecipes()
    }

    suspend fun saveRecipe(request: SavedRecipeCreateRequest): SavedRecipeResponse {
        return apiService.createSavedRecipe(request)
    }

    suspend fun unsaveRecipe(savedRecipeId: Int) {
        apiService.deleteSavedRecipe(savedRecipeId)
    }
}
