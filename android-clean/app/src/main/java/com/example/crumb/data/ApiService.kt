package com.example.crumb.data

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("ingredients/categories")
    suspend fun getIngredientCategories(): List<IngredientResponse>

    @GET("recipes/user-created")
    suspend fun getUserRecipes(): List<RecipeResponse>

    @POST("recipes/user-created")
    suspend fun createUserRecipe(@Body request: RecipeRequest): RecipeResponse

    @GET("recipes/user-created/{recipe_id}")
    suspend fun getUserRecipe(@Path("recipe_id") recipeId: Int): RecipeResponse

    @PATCH("recipes/user-created/{recipe_id}")
    suspend fun updateUserRecipe(
        @Path("recipe_id") recipeId: Int,
        @Body request: RecipeRequest
    ): RecipeResponse

    @DELETE("recipes/user-created/{recipe_id}")
    suspend fun deleteUserRecipe(@Path("recipe_id") recipeId: Int): Map<String, String>
}
