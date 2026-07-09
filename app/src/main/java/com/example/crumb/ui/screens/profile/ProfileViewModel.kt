package com.example.crumb.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.HealthRepository
import com.example.crumb.data.repository.SavedRecipeRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import com.example.crumb.ui.screens.recipes.SavedRecipeUiState
import com.example.crumb.ui.screens.recipes.toUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class ProfileViewModel(
    private val healthRepository: HealthRepository = HealthRepository(),
    private val savedRecipeRepository: SavedRecipeRepository = SavedRecipeRepository()
) : ViewModel() {
    private val _healthUiState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val healthUiState: StateFlow<HealthUiState> = _healthUiState.asStateFlow()

    private val _savedRecipeUiState = MutableStateFlow<SavedRecipeUiState>(
        SavedRecipeUiState.Loading
    )
    val savedRecipeUiState: StateFlow<SavedRecipeUiState> = _savedRecipeUiState.asStateFlow()

    init {
        checkBackendHealth()
        loadSavedRecipes()
    }

    fun checkBackendHealth() {
        _healthUiState.value = HealthUiState.Loading

        viewModelScope.launch {
            _healthUiState.value = try {
                val response = healthRepository.checkHealth()
                HealthUiState.Success(status = response.status)
            } catch (exception: IOException) {
                HealthUiState.Error(
                    message = "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: Exception) {
                HealthUiState.Error(
                    message = exception.message ?: "Backend health check failed."
                )
            }
        }
    }

    fun loadSavedRecipes() {
        _savedRecipeUiState.value = SavedRecipeUiState.Loading

        viewModelScope.launch {
            _savedRecipeUiState.value = try {
                val recipes = savedRecipeRepository.getSavedRecipes().map { it.toUiModel() }
                if (recipes.isEmpty()) {
                    SavedRecipeUiState.Empty
                } else {
                    SavedRecipeUiState.Success(recipes)
                }
            } catch (exception: IOException) {
                SavedRecipeUiState.Error(
                    message = "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: Exception) {
                SavedRecipeUiState.Error(
                    message = exception.toOperationMessage("Could not load saved recipes.")
                )
            }
        }
    }
}
