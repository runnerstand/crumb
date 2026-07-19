package com.example.crumb.data

import com.squareup.moshi.Json

data class SavedRecipeRequest(
    @param:Json(name = "recipe_id")
    val recipeId: Int
)

data class SavedRecipeStatusResponse(
    @param:Json(name = "recipe_id")
    val recipeId: Int,
    @param:Json(name = "is_saved")
    val isSaved: Boolean
)

data class SavedRecipeResponse(
    val id: Int,
    @param:Json(name = "user_id")
    val userId: String,
    @param:Json(name = "recipe_id")
    val recipeId: Int,
    val recipe: RecipeResponse,
    @param:Json(name = "created_at")
    val createdAt: String
)
