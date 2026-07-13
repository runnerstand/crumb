package com.example.crumb.ui.screens.home

import com.example.crumb.core.config.AppConfig
import com.example.crumb.data.remote.model.CommunityPostResponse
import com.example.crumb.ui.screens.recipes.RecipeUiModel

sealed interface CommunityPostUiState {
    data object Loading : CommunityPostUiState
    data object Empty : CommunityPostUiState
    data class Success(val posts: List<CommunityPostUiModel>) : CommunityPostUiState
    data class Error(val message: String) : CommunityPostUiState
}

sealed interface HomeDashboardUiState {
    data object Loading : HomeDashboardUiState
    data class Success(
        val dailyRecipe: RecipeUiModel?,
        val categories: List<String>
    ) : HomeDashboardUiState
    data class Error(val message: String) : HomeDashboardUiState
}

data class CommunityPostUiModel(
    val id: Int,
    val creatorId: String,
    val recipeId: Int?,
    val linkedRecipe: RecipeUiModel?,
    val creatorName: String,
    val title: String,
    val ingredients: List<String>,
    val caption: String,
    val createdAt: String,
    val updatedAt: String,
    val isOwnedByLocalUser: Boolean
)

fun CommunityPostResponse.toUiModel(recipesById: Map<Int, RecipeUiModel> = emptyMap()): CommunityPostUiModel {
    return CommunityPostUiModel(
        id = id,
        creatorId = creatorId,
        recipeId = recipeId,
        linkedRecipe = recipeId?.let { recipesById[it] },
        creatorName = creatorName,
        title = title,
        ingredients = ingredientsJson,
        caption = caption,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOwnedByLocalUser = creatorId == AppConfig.TEMP_USER_ID
    )
}
