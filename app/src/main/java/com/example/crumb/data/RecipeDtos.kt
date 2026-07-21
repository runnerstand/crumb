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
    val cookingTimeMinutes: Int? = null,
    val servings: Int = 2,
    @param:Json(name = "image_url")
    val imageUrl: String? = null
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
    val servings: Int = 2,
    @param:Json(name = "image_url")
    val imageUrl: String? = null,
    @param:Json(name = "average_rating")
    val averageRating: Double? = null,
    @param:Json(name = "rating_count")
    val ratingCount: Int = 0,
    @param:Json(name = "user_rating")
    val userRating: Int? = null,
    @param:Json(name = "created_at")
    val createdAt: String
)

data class RecipeImageUploadResponse(
    @param:Json(name = "image_url")
    val imageUrl: String,
    @param:Json(name = "sha256_hash")
    val sha256Hash: String,
    @param:Json(name = "content_type")
    val contentType: String
)
