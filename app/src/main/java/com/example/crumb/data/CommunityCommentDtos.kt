package com.example.crumb.data

import com.squareup.moshi.Json

data class CommunityCommentRequest(
    @param:Json(name = "comment_text")
    val commentText: String,
    val rating: Int? = null
)

data class CommunityCommentResponse(
    val id: Int,
    @param:Json(name = "post_id")
    val postId: Int,
    @param:Json(name = "creator_id")
    val creatorId: String,
    @param:Json(name = "creator_name")
    val creatorName: String,
    @param:Json(name = "comment_text")
    val commentText: String,
    @param:Json(name = "created_at")
    val createdAt: String,
    @param:Json(name = "updated_at")
    val updatedAt: String
)
