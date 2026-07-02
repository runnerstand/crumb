package com.example.crumb.ui.screens.profile

sealed interface HealthUiState {
    data object Loading : HealthUiState

    data class Success(
        val status: String
    ) : HealthUiState

    data class Error(
        val message: String
    ) : HealthUiState
}
