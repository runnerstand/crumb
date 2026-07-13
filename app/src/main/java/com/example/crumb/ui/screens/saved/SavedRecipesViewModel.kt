package com.example.crumb.ui.screens.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.SavedRecipeRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import com.example.crumb.ui.screens.recipes.SavedRecipeUiModel
import com.example.crumb.ui.screens.recipes.SavedRecipeUiState
import com.example.crumb.ui.screens.recipes.toUiModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedRecipesViewModel(
    private val repository: SavedRecipeRepository = SavedRecipeRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<SavedRecipeUiState>(SavedRecipeUiState.Loading)
    val uiState: StateFlow<SavedRecipeUiState> = _uiState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        loadSavedRecipes()
    }

    fun loadSavedRecipes() {
        _uiState.value = SavedRecipeUiState.Loading

        viewModelScope.launch {
            _uiState.value = try {
                val recipes = repository.getSavedRecipes().map { it.toUiModel() }
                if (recipes.isEmpty()) {
                    SavedRecipeUiState.Empty
                } else {
                    SavedRecipeUiState.Success(recipes)
                }
            } catch (exception: IOException) {
                SavedRecipeUiState.Error(
                    "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: Exception) {
                SavedRecipeUiState.Error(
                    exception.toOperationMessage("Could not load saved recipes.")
                )
            }
        }
    }

    fun unsaveRecipe(recipe: SavedRecipeUiModel) {
        viewModelScope.launch {
            try {
                repository.unsaveRecipe(recipe.id)
                _operationMessage.value = "Recipe unsaved."
                loadSavedRecipes()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not unsave recipe.")
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }
}
