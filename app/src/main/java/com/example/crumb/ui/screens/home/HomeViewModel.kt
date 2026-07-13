package com.example.crumb.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.CommunityPostRepository
import com.example.crumb.data.repository.IngredientRepository
import com.example.crumb.data.repository.RecipeRepository
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.recipes.toUiModel
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeViewModel(
    private val repository: CommunityPostRepository = CommunityPostRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository(),
    private val ingredientRepository: IngredientRepository = IngredientRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<CommunityPostUiState>(CommunityPostUiState.Loading)
    val uiState: StateFlow<CommunityPostUiState> = _uiState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _recipes = MutableStateFlow<List<RecipeUiModel>>(emptyList())
    val recipes: StateFlow<List<RecipeUiModel>> = _recipes.asStateFlow()

    private val _dashboardUiState = MutableStateFlow<HomeDashboardUiState>(
        HomeDashboardUiState.Loading
    )
    val dashboardUiState: StateFlow<HomeDashboardUiState> = _dashboardUiState.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts() {
        _uiState.value = CommunityPostUiState.Loading

        viewModelScope.launch {
            _uiState.value = try {
                val recipes = try {
                    recipeRepository.getRecipes().map { it.toUiModel() }
                } catch (exception: Exception) {
                    _operationMessage.value = exception.toOperationMessage(
                        "Could not load recipes for linked post details."
                    )
                    emptyList()
                }
                _recipes.value = recipes
                loadDashboard(recipes)

                val recipesById = recipes.associateBy { it.id }
                val posts = repository.getPosts().map { it.toUiModel(recipesById) }
                if (posts.isEmpty()) {
                    CommunityPostUiState.Empty
                } else {
                    CommunityPostUiState.Success(posts)
                }
            } catch (exception: IOException) {
                CommunityPostUiState.Error("Could not reach the backend. Make sure FastAPI is running.")
            } catch (exception: HttpException) {
                CommunityPostUiState.Error(exception.toOperationMessage("Could not load community posts."))
            } catch (exception: Exception) {
                CommunityPostUiState.Error(exception.message ?: "Could not load community posts.")
            }
        }
    }

    private suspend fun loadDashboard(recipes: List<RecipeUiModel>) {
        _dashboardUiState.value = try {
            val categories = ingredientRepository.getIngredients()
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .take(8)

            HomeDashboardUiState.Success(
                dailyRecipe = selectDailyRecipe(recipes),
                categories = categories
            )
        } catch (exception: Exception) {
            HomeDashboardUiState.Error(
                exception.toOperationMessage("Could not load dashboard.")
            )
        }
    }

    private fun selectDailyRecipe(recipes: List<RecipeUiModel>): RecipeUiModel? {
        if (recipes.isEmpty()) {
            return null
        }

        val dayIndex = LocalDate.now().dayOfYear % recipes.size
        return recipes.sortedBy { it.id }[dayIndex]
    }

    fun updatePost(
        postId: Int,
        title: String,
        ingredients: List<String>,
        caption: String,
        recipeId: Int?
    ) {
        viewModelScope.launch {
            try {
                repository.updatePost(
                    postId = postId,
                    title = title.trim(),
                    ingredients = ingredients.map { it.trim() },
                    caption = caption.trim(),
                    recipeId = recipeId
                )
                _operationMessage.value = "Post updated."
                loadPosts()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not update post.")
            }
        }
    }

    fun deletePost(postId: Int) {
        viewModelScope.launch {
            try {
                repository.deletePost(postId)
                _operationMessage.value = "Post deleted."
                loadPosts()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not delete post.")
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }
}

fun Exception.toOperationMessage(fallback: String): String {
    return when (this) {
        is IOException -> "Could not reach the backend. Make sure FastAPI is running."
        is HttpException -> {
            val errorBody = response()?.errorBody()?.string()?.trim()
            if (errorBody.isNullOrBlank()) {
                "Backend error ${code()}: ${message()}"
            } else {
                "Backend error ${code()}: $errorBody"
            }
        }
        else -> message ?: fallback
    }
}
