package com.example.crumb.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.CommunityPostRepository
import com.example.crumb.data.repository.IngredientRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CreatePostViewModel(
    private val repository: CommunityPostRepository = CommunityPostRepository(),
    private val ingredientRepository: IngredientRepository = IngredientRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<CreatePostUiState>(CreatePostUiState.Idle)
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private val _catalogueState = MutableStateFlow<IngredientCatalogueUiState>(
        IngredientCatalogueUiState.Loading
    )
    val catalogueState: StateFlow<IngredientCatalogueUiState> = _catalogueState.asStateFlow()

    private val _selectedIngredients = MutableStateFlow<List<String>>(emptyList())
    val selectedIngredients: StateFlow<List<String>> = _selectedIngredients.asStateFlow()

    init {
        loadIngredientCatalogue()
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

    fun addIngredient(name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) {
            return
        }
        if (_selectedIngredients.value.any { it == cleanedName }) {
            return
        }

        _selectedIngredients.value = _selectedIngredients.value + cleanedName
    }

    fun removeIngredient(name: String) {
        _selectedIngredients.value = _selectedIngredients.value.filterNot { it == name }
    }

    fun createPost(title: String, caption: String) {
        val cleanedTitle = title.trim()
        val ingredients = _selectedIngredients.value
        val cleanedCaption = caption.trim()

        when {
            cleanedTitle.isBlank() -> {
                _uiState.value = CreatePostUiState.Error("Title is required.")
                return
            }

            ingredients.isEmpty() -> {
                _uiState.value = CreatePostUiState.Error("Select at least one ingredient.")
                return
            }

            cleanedCaption.length > 200 -> {
                _uiState.value = CreatePostUiState.Error("Caption must be 200 characters or fewer.")
                return
            }
        }

        _uiState.value = CreatePostUiState.Saving

        viewModelScope.launch {
            _uiState.value = try {
                repository.createPost(
                    title = cleanedTitle,
                    ingredients = ingredients,
                    caption = cleanedCaption
                )
                _selectedIngredients.value = emptyList()
                CreatePostUiState.Success
            } catch (exception: Exception) {
                CreatePostUiState.Error(exception.toOperationMessage("Could not create post."))
            }
        }
    }

    fun resetState() {
        _uiState.value = CreatePostUiState.Idle
    }
}
