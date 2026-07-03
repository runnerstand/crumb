package com.example.crumb.ui.screens.comments

import com.example.crumb.core.config.AppConfig
import com.example.crumb.data.remote.model.CommunityCommentResponse

sealed interface CommunityCommentUiState {
    data object Loading : CommunityCommentUiState
    data object Empty : CommunityCommentUiState
    data class Success(val comments: List<CommunityCommentUiModel>) : CommunityCommentUiState
    data class Error(val message: String) : CommunityCommentUiState
}

data class CommunityCommentUiModel(
    val id: Int,
    val postId: Int,
    val creatorId: String,
    val creatorName: String,
    val commentText: String,
    val createdAt: String,
    val updatedAt: String,
    val isOwnedByLocalUser: Boolean
)

fun CommunityCommentResponse.toUiModel(): CommunityCommentUiModel {
    return CommunityCommentUiModel(
        id = id,
        postId = postId,
        creatorId = creatorId,
        creatorName = creatorName,
        commentText = commentText,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOwnedByLocalUser = creatorId == AppConfig.TEMP_USER_ID
    )
}
