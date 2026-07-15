package com.example.crumb.data

class RecipeRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getUserRecipes(): Result<List<RecipeResponse>> {
        return runCatching {
            apiService.getUserRecipes()
        }
    }

    suspend fun getUserRecipe(recipeId: Int): Result<RecipeResponse> {
        return runCatching {
            apiService.getUserRecipe(recipeId)
        }
    }

    suspend fun createUserRecipe(request: RecipeRequest): Result<RecipeResponse> {
        return runCatching {
            apiService.createUserRecipe(request)
        }
    }

    suspend fun updateUserRecipe(recipeId: Int, request: RecipeRequest): Result<RecipeResponse> {
        return runCatching {
            apiService.updateUserRecipe(recipeId, request)
        }
    }

    suspend fun deleteUserRecipe(recipeId: Int): Result<Unit> {
        return runCatching {
            apiService.deleteUserRecipe(recipeId)
            Unit
        }
    }
}
