package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.IngredientCategoryResponse

class IngredientRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getIngredients(): List<IngredientCategoryResponse> {
        return apiService.getIngredientCategories()
    }
}
