package com.example.crumb.data

data class IngredientResponse(
    val name: String,
    val category: String,
    val tags: List<String> = emptyList()
)
