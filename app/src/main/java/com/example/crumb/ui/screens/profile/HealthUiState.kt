package com.example.crumb.ui.screens.profile

import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.recipes.SavedRecipeUiModel

sealed interface HealthUiState {
    data object Loading : HealthUiState

    data class Success(
        val status: String
    ) : HealthUiState

    data class Error(
        val message: String
    ) : HealthUiState
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(
        val myRecipes: List<RecipeUiModel>,
        val savedRecipes: List<SavedRecipeUiModel>,
        val myPostsCount: Int,
        val backendStatus: String
    ) : ProfileUiState

    data class Error(
        val message: String,
        val backendStatus: String
    ) : ProfileUiState
}
