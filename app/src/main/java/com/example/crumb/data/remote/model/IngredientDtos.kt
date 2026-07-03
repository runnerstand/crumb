package com.example.crumb.data.remote.model

data class IngredientCategoryResponse(
    val name: String,
    val category: String,
    val tags: List<String> = emptyList()
)
