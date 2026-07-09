package com.example.crumb.data.remote.model

import com.squareup.moshi.Json

data class RecipeIngredientRequest(
    @Json(name = "ingredient_name")
    val ingredientName: String,
    val quantity: String? = null,
    val unit: String? = null
)

data class RecipeCreateRequest(
    val title: String,
    val ingredients: List<RecipeIngredientRequest>,
    val instructions: List<String>,
    @Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int? = null
)

data class RecipeIngredientResponse(
    @Json(name = "ingredient_id")
    val ingredientId: Int,
    @Json(name = "ingredient_name")
    val ingredientName: String,
    val quantity: String? = null,
    val unit: String? = null
)

data class RecipeResponse(
    val id: Int,
    @Json(name = "user_id")
    val userId: String,
    val title: String,
    val ingredients: List<RecipeIngredientResponse> = emptyList(),
    val instructions: List<String> = emptyList(),
    @Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int? = null,
    @Json(name = "created_at")
    val createdAt: String
)
