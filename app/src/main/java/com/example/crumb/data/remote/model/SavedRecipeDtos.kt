package com.example.crumb.data.remote.model

import com.squareup.moshi.Json

data class SavedRecipeCreateRequest(
    @Json(name = "recipe_id")
    val recipeId: Int? = null,
    val title: String,
    val ingredients: List<String>,
    @Json(name = "missing_ingredients")
    val missingIngredients: List<String> = emptyList(),
    val instructions: List<String>,
    @Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int
)

data class SavedRecipeResponse(
    val id: Int,
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "recipe_id")
    val recipeId: Int? = null,
    val title: String,
    val ingredients: List<String> = emptyList(),
    @Json(name = "missing_ingredients")
    val missingIngredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    @Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int,
    @Json(name = "created_at")
    val createdAt: String
)
