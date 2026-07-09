package com.example.crumb.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.remote.model.RecipeIngredientRequest
import com.example.crumb.data.repository.IngredientRepository
import com.example.crumb.data.repository.RecipeRepository
import com.example.crumb.ui.screens.create.IngredientCatalogueUiState
import com.example.crumb.ui.screens.create.IngredientPickerItem
import com.example.crumb.ui.screens.home.toOperationMessage
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RecipesViewModel(
    private val repository: RecipeRepository = RecipeRepository(),
    private val ingredientRepository: IngredientRepository = IngredientRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecipeUiState>(RecipeUiState.Loading)
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    private val _catalogueState = MutableStateFlow<IngredientCatalogueUiState>(
        IngredientCatalogueUiState.Loading
    )
    val catalogueState: StateFlow<IngredientCatalogueUiState> = _catalogueState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        loadRecipes()
        loadIngredientCatalogue()
    }

    fun loadRecipes() {
        _uiState.value = RecipeUiState.Loading

        viewModelScope.launch {
            _uiState.value = try {
                val recipes = repository.getRecipes().map { it.toUiModel() }
                if (recipes.isEmpty()) RecipeUiState.Empty else RecipeUiState.Success(recipes)
            } catch (exception: IOException) {
                RecipeUiState.Error("Could not reach the backend. Make sure FastAPI is running.")
            } catch (exception: HttpException) {
                RecipeUiState.Error(exception.toOperationMessage("Could not load recipes."))
            } catch (exception: Exception) {
                RecipeUiState.Error(exception.message ?: "Could not load recipes.")
            }
        }
    }

    fun loadIngredientCatalogue() {
        _catalogueState.value = IngredientCatalogueUiState.Loading

        viewModelScope.launch {
            _catalogueState.value = try {
                val ingredients = ingredientRepository.getIngredients()
                    .map {
                        IngredientPickerItem(
                            name = it.name,
                            category = it.category,
                            tags = it.tags
                        )
                    }
                    .filter { it.name.isNotBlank() }
                    .distinctBy { it.name }
                    .sortedWith(compareBy<IngredientPickerItem> { it.category }.thenBy { it.name })

                if (ingredients.isEmpty()) {
                    IngredientCatalogueUiState.Empty
                } else {
                    IngredientCatalogueUiState.Success(ingredients)
                }
            } catch (exception: IOException) {
                IngredientCatalogueUiState.Error(
                    "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: HttpException) {
                IngredientCatalogueUiState.Error(
                    exception.toOperationMessage("Could not load ingredients.")
                )
            } catch (exception: Exception) {
                IngredientCatalogueUiState.Error(exception.message ?: "Could not load ingredients.")
            }
        }
    }

    fun createRecipe(
        title: String,
        ingredients: List<RecipeIngredientRequest>,
        instructions: List<String>,
        cookingTimeMinutes: Int?
    ) {
        saveRecipe(null, title, ingredients, instructions, cookingTimeMinutes)
    }

    fun updateRecipe(
        recipeId: Int,
        title: String,
        ingredients: List<RecipeIngredientRequest>,
        instructions: List<String>,
        cookingTimeMinutes: Int?
    ) {
        saveRecipe(recipeId, title, ingredients, instructions, cookingTimeMinutes)
    }

    private fun saveRecipe(
        recipeId: Int?,
        title: String,
        ingredients: List<RecipeIngredientRequest>,
        instructions: List<String>,
        cookingTimeMinutes: Int?
    ) {
        viewModelScope.launch {
            try {
                if (recipeId == null) {
                    repository.createRecipe(
                        title = title.trim(),
                        ingredients = ingredients,
                        instructions = instructions,
                        cookingTimeMinutes = cookingTimeMinutes
                    )
                    _operationMessage.value = "Recipe created."
                } else {
                    repository.updateRecipe(
                        recipeId = recipeId,
                        title = title.trim(),
                        ingredients = ingredients,
                        instructions = instructions,
                        cookingTimeMinutes = cookingTimeMinutes
                    )
                    _operationMessage.value = "Recipe updated."
                }
                loadRecipes()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not save recipe.")
            }
        }
    }

    fun deleteRecipe(recipeId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteRecipe(recipeId)
                _operationMessage.value = "Recipe deleted."
                loadRecipes()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not delete recipe.")
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }
}
