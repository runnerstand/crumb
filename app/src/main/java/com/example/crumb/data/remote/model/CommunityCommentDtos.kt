package com.example.crumb.data.remote.model

import com.squareup.moshi.Json

data class CommunityCommentCreateRequest(
    @Json(name = "comment_text")
    val commentText: String
)

data class CommunityCommentResponse(
    val id: Int,
    @Json(name = "post_id")
    val postId: Int,
    @Json(name = "creator_id")
    val creatorId: String,
    @Json(name = "creator_name")
    val creatorName: String,
    @Json(name = "comment_text")
    val commentText: String,
    @Json(name = "created_at")
    val createdAt: String,
    @Json(name = "updated_at")
    val updatedAt: String
)
