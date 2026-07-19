package com.example.crumb.data

import com.squareup.moshi.Json

data class RecipeIngredientRequest(
    @param:Json(name = "ingredient_name")
    val ingredientName: String,
    val quantity: String? = null,
    val unit: String? = null
)

data class RecipeRequest(
    val title: String,
    val ingredients: List<RecipeIngredientRequest>,
    val instructions: List<String>,
    @param:Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int? = null
)

data class RecipeIngredientResponse(
    @param:Json(name = "ingredient_id")
    val ingredientId: Int,
    @param:Json(name = "ingredient_name")
    val ingredientName: String,
    val quantity: String? = null,
    val unit: String? = null
)

data class RecipeResponse(
    val id: Int,
    @param:Json(name = "user_id")
    val userId: String,
    @param:Json(name = "creator_name")
    val creatorName: String? = null,
    val title: String,
    val ingredients: List<RecipeIngredientResponse>,
    val instructions: List<String>,
    @param:Json(name = "cooking_time_minutes")
    val cookingTimeMinutes: Int? = null,
    @param:Json(name = "average_rating")
    val averageRating: Double? = null,
    @param:Json(name = "rating_count")
    val ratingCount: Int = 0,
    @param:Json(name = "user_rating")
    val userRating: Int? = null,
    @param:Json(name = "created_at")
    val createdAt: String
)
