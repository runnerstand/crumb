package com.example.crumb.ui.screens.recipes

import com.example.crumb.data.remote.model.RecipeIngredientRequest
import com.example.crumb.data.remote.model.RecipeResponse
import com.example.crumb.data.remote.model.SavedRecipeCreateRequest
import com.example.crumb.data.remote.model.SavedRecipeResponse

sealed interface RecipeUiState {
    data object Loading : RecipeUiState
    data object Empty : RecipeUiState
    data class Success(val recipes: List<RecipeUiModel>) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

data class RecipeUiModel(
    val id: Int,
    val userId: String,
    val title: String,
    val ingredients: List<RecipeIngredientUiModel>,
    val instructions: List<String>,
    val cookingTimeMinutes: Int?,
    val createdAt: String,
    val isOwnedByLocalUser: Boolean,
    val savedRecipeId: Int? = null
) {
    val isSaved: Boolean
        get() = savedRecipeId != null

    fun toSavedRecipeCreateRequest(): SavedRecipeCreateRequest {
        return SavedRecipeCreateRequest(
            recipeId = id,
            title = title,
            ingredients = ingredients.map { it.displayText() },
            instructions = instructions,
            cookingTimeMinutes = cookingTimeMinutes ?: 1
        )
    }
}

sealed interface SavedRecipeUiState {
    data object Loading : SavedRecipeUiState
    data object Empty : SavedRecipeUiState
    data class Success(val recipes: List<SavedRecipeUiModel>) : SavedRecipeUiState
    data class Error(val message: String) : SavedRecipeUiState
}

data class SavedRecipeUiModel(
    val id: Int,
    val userId: String,
    val recipeId: Int?,
    val title: String,
    val ingredients: List<String>,
    val missingIngredients: List<String>,
    val instructions: List<String>,
    val cookingTimeMinutes: Int,
    val createdAt: String
)

data class RecipeIngredientUiModel(
    val ingredientName: String,
    val quantity: String?,
    val unit: String?
) {
    fun displayText(): String {
        val measurement = listOfNotNull(quantity, unit)
            .joinToString(" ")
            .trim()
        return if (measurement.isBlank()) ingredientName else "$measurement $ingredientName"
    }
}

data class EditableRecipeIngredient(
    val ingredientName: String,
    val quantity: String = "",
    val unit: String = ""
) {
    fun toRequest(): RecipeIngredientRequest {
        return RecipeIngredientRequest(
            ingredientName = ingredientName,
            quantity = quantity.trim().ifBlank { null },
            unit = unit.trim().ifBlank { null }
        )
    }
}

fun RecipeResponse.toUiModel(): RecipeUiModel {
    return RecipeUiModel(
        id = id,
        userId = userId,
        title = title,
        ingredients = ingredients.map {
            RecipeIngredientUiModel(
                ingredientName = it.ingredientName,
                quantity = it.quantity,
                unit = it.unit
            )
        },
        instructions = instructions,
        cookingTimeMinutes = cookingTimeMinutes,
        createdAt = createdAt,
        isOwnedByLocalUser = userId == "local-user"
    )
}

fun SavedRecipeResponse.toUiModel(): SavedRecipeUiModel {
    return SavedRecipeUiModel(
        id = id,
        userId = userId,
        recipeId = recipeId,
        title = title,
        ingredients = ingredients,
        missingIngredients = missingIngredients,
        instructions = instructions,
        cookingTimeMinutes = cookingTimeMinutes,
        createdAt = createdAt
    )
}
