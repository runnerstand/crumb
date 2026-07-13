package com.example.crumb.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.core.config.AppConfig
import com.example.crumb.data.repository.CommunityPostRepository
import com.example.crumb.data.repository.HealthRepository
import com.example.crumb.data.repository.RecipeRepository
import com.example.crumb.data.repository.SavedRecipeRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import com.example.crumb.ui.screens.recipes.toUiModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val healthRepository: HealthRepository = HealthRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository(),
    private val savedRecipeRepository: SavedRecipeRepository = SavedRecipeRepository(),
    private val communityPostRepository: CommunityPostRepository = CommunityPostRepository()
) : ViewModel() {
    private val _profileUiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        _profileUiState.value = ProfileUiState.Loading

        viewModelScope.launch {
            val backendStatus = try {
                healthRepository.checkHealth().status
            } catch (exception: IOException) {
                "Offline"
            } catch (exception: Exception) {
                exception.message ?: "Unavailable"
            }

            _profileUiState.value = try {
                val myRecipes = recipeRepository.getRecipes()
                    .map { it.toUiModel() }
                    .filter { it.userId == AppConfig.TEMP_USER_ID }
                val savedRecipes = savedRecipeRepository.getSavedRecipes().map { it.toUiModel() }
                val myPostsCount = communityPostRepository.getPosts()
                    .count { it.creatorId == AppConfig.TEMP_USER_ID }

                ProfileUiState.Success(
                    myRecipes = myRecipes,
                    savedRecipes = savedRecipes,
                    myPostsCount = myPostsCount,
                    backendStatus = backendStatus
                )
            } catch (exception: Exception) {
                ProfileUiState.Error(
                    message = exception.toOperationMessage("Could not load profile."),
                    backendStatus = backendStatus
                )
            }
        }
    }
}
