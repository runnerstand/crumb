package com.example.crumb.ui.screens.search

import com.example.crumb.ui.screens.recipes.RecipeUiModel

sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Empty(val categories: List<String>) : SearchUiState
    data class Success(
        val recipes: List<RecipeUiModel>,
        val categories: List<String>
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

data class RecipeSearchFilters(
    val keyword: String = "",
    val category: String? = null,
    val maxCookingTimeMinutes: Int? = null,
    val savedOnly: Boolean = false
)
