package com.example.crumb.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.IngredientRepository
import com.example.crumb.data.repository.RecipeRepository
import com.example.crumb.data.repository.SavedRecipeRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.recipes.toUiModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SearchViewModel(
    private val recipeRepository: RecipeRepository = RecipeRepository(),
    private val ingredientRepository: IngredientRepository = IngredientRepository(),
    private val savedRecipeRepository: SavedRecipeRepository = SavedRecipeRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private var allRecipes: List<RecipeUiModel> = emptyList()
    private var categoryByIngredient: Map<String, String> = emptyMap()
    private var categories: List<String> = emptyList()
    private var filters: RecipeSearchFilters = RecipeSearchFilters()

    init {
        loadSearchData()
    }

    fun loadSearchData() {
        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            try {
                val savedRecipeIds = savedRecipeRepository.getSavedRecipes()
                    .mapNotNull { savedRecipe ->
                        savedRecipe.recipeId?.let { recipeId -> recipeId to savedRecipe.id }
                    }
                    .toMap()
                val ingredients = ingredientRepository.getIngredients()
                    .filter { it.name.isNotBlank() && it.category.isNotBlank() }
                categoryByIngredient = ingredients.associate {
                    it.name.lowercase() to it.category
                }
                categories = ingredients.map { it.category }.distinct().sorted()
                allRecipes = recipeRepository.getRecipes().map {
                    it.toUiModel().copy(savedRecipeId = savedRecipeIds[it.id])
                }
                renderFilteredRecipes()
            } catch (exception: IOException) {
                _uiState.value = SearchUiState.Error(
                    "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: HttpException) {
                _uiState.value = SearchUiState.Error(
                    exception.toOperationMessage("Could not load recipes.")
                )
            } catch (exception: Exception) {
                _uiState.value = SearchUiState.Error(exception.message ?: "Could not load recipes.")
            }
        }
    }

    fun updateFilters(nextFilters: RecipeSearchFilters) {
        filters = nextFilters
        renderFilteredRecipes()
    }

    fun clearFilters() {
        filters = RecipeSearchFilters()
        renderFilteredRecipes()
    }

    fun saveRecipe(recipe: RecipeUiModel) {
        if (recipe.isSaved) {
            return
        }

        viewModelScope.launch {
            try {
                savedRecipeRepository.saveRecipe(recipe.toSavedRecipeCreateRequest())
                _operationMessage.value = "Recipe saved."
                loadSearchData()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not save recipe.")
                loadSearchData()
            }
        }
    }

    fun unsaveRecipe(recipe: RecipeUiModel) {
        val savedRecipeId = recipe.savedRecipeId ?: return

        viewModelScope.launch {
            try {
                savedRecipeRepository.unsaveRecipe(savedRecipeId)
                _operationMessage.value = "Recipe unsaved."
                loadSearchData()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not unsave recipe.")
                loadSearchData()
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun renderFilteredRecipes() {
        val keyword = filters.keyword.trim()
        val maxCookingTimeMinutes = filters.maxCookingTimeMinutes
        val filteredRecipes = allRecipes
            .filter { recipe ->
                keyword.isBlank() ||
                    recipe.title.contains(keyword, ignoreCase = true) ||
                    recipe.ingredients.any {
                        it.ingredientName.contains(keyword, ignoreCase = true) ||
                            it.displayText().contains(keyword, ignoreCase = true)
                    }
            }
            .filter { recipe ->
                filters.category == null ||
                    recipe.ingredients.any {
                        categoryByIngredient[it.ingredientName.lowercase()] == filters.category
                    }
            }
            .filter { recipe ->
                maxCookingTimeMinutes == null ||
                    (recipe.cookingTimeMinutes ?: Int.MAX_VALUE) <= maxCookingTimeMinutes
            }
            .filter { recipe -> !filters.savedOnly || recipe.isSaved }

        _uiState.value = if (filteredRecipes.isEmpty()) {
            SearchUiState.Empty(categories)
        } else {
            SearchUiState.Success(filteredRecipes, categories)
        }
    }
}
