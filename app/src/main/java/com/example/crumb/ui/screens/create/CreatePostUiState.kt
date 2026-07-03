package com.example.crumb.ui.screens.create

sealed interface CreatePostUiState {
    data object Idle : CreatePostUiState
    data object Saving : CreatePostUiState
    data object Success : CreatePostUiState
    data class Error(val message: String) : CreatePostUiState
}

sealed interface IngredientCatalogueUiState {
    data object Loading : IngredientCatalogueUiState
    data object Empty : IngredientCatalogueUiState
    data class Success(val ingredients: List<IngredientPickerItem>) : IngredientCatalogueUiState
    data class Error(val message: String) : IngredientCatalogueUiState
}

data class IngredientPickerItem(
    val name: String,
    val category: String,
    val tags: List<String>
)
