package com.example.crumb.ui.screens.home

import com.example.crumb.core.config.AppConfig
import com.example.crumb.data.remote.model.CommunityPostResponse

sealed interface CommunityPostUiState {
    data object Loading : CommunityPostUiState
    data object Empty : CommunityPostUiState
    data class Success(val posts: List<CommunityPostUiModel>) : CommunityPostUiState
    data class Error(val message: String) : CommunityPostUiState
}

data class CommunityPostUiModel(
    val id: Int,
    val creatorId: String,
    val creatorName: String,
    val title: String,
    val ingredients: List<String>,
    val caption: String,
    val createdAt: String,
    val updatedAt: String,
    val isOwnedByLocalUser: Boolean
)

fun CommunityPostResponse.toUiModel(): CommunityPostUiModel {
    return CommunityPostUiModel(
        id = id,
        creatorId = creatorId,
        creatorName = creatorName,
        title = title,
        ingredients = ingredientsJson,
        caption = caption,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOwnedByLocalUser = creatorId == AppConfig.TEMP_USER_ID
    )
}
