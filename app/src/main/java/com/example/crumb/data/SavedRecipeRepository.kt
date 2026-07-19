package com.example.crumb.data

class SavedRecipeRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getSavedRecipes(): Result<List<SavedRecipeResponse>> {
        return safeApiCall {
            apiService.getSavedRecipes()
        }
    }

    suspend fun saveRecipe(recipeId: Int): Result<SavedRecipeResponse> {
        return safeApiCall {
            apiService.saveRecipe(SavedRecipeRequest(recipeId))
        }
    }

    suspend fun getSavedRecipeStatus(recipeId: Int): Result<SavedRecipeStatusResponse> {
        return safeApiCall {
            apiService.getSavedRecipeStatus(recipeId)
        }
    }

    suspend fun unsaveRecipe(recipeId: Int): Result<Unit> {
        return safeApiCall {
            apiService.deleteSavedRecipe(recipeId)
            Unit
        }
    }
}
