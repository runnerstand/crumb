package com.example.crumb.ui.screens.recipes

import com.example.crumb.data.remote.model.RecipeIngredientRequest
import com.example.crumb.data.remote.model.RecipeResponse

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
    val isOwnedByLocalUser: Boolean
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
