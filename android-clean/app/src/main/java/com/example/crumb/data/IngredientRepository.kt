package com.example.crumb.data

class IngredientRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getIngredientCategories(): Result<List<IngredientResponse>> {
        return runCatching {
            apiService.getIngredientCategories()
        }
    }
}
