package com.example.crumb.data.remote.model

import com.squareup.moshi.Json

data class CommunityPostCreateRequest(
    @Json(name = "title")
    val title: String,
    @Json(name = "ingredients")
    val ingredients: List<String>,
    @Json(name = "caption")
    val caption: String
)

data class CommunityPostResponse(
    val id: Int,
    @Json(name = "creator_id")
    val creatorId: String,
    @Json(name = "creator_name")
    val creatorName: String,
    val title: String,
    @Json(name = "ingredients_json")
    val ingredientsJson: List<String> = emptyList(),
    val caption: String,
    @Json(name = "created_at")
    val createdAt: String,
    @Json(name = "updated_at")
    val updatedAt: String
)

data class DeleteMessageResponse(
    val message: String
)
