package com.example.crumb.data

import com.squareup.moshi.Json

data class CommunityPostRequest(
    val title: String = "",
    val ingredients: List<String> = emptyList(),
    val caption: String = "",
    @param:Json(name = "recipe_id")
    val recipeId: Int? = null
)

data class CommunityPostResponse(
    val id: Int,
    @param:Json(name = "creator_id")
    val creatorId: String,
    @param:Json(name = "creator_name")
    val creatorName: String,
    @param:Json(name = "recipe_id")
    val recipeId: Int? = null,
    val title: String,
    @param:Json(name = "ingredients_json")
    val ingredientsJson: List<String>,
    val caption: String,
    val recipe: RecipeResponse? = null,
    @param:Json(name = "created_at")
    val createdAt: String,
    @param:Json(name = "updated_at")
    val updatedAt: String
)
