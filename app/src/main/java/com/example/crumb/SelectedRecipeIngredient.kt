package com.example.crumb

data class SelectedRecipeIngredient(
    val name: String,
    var quantity: String = "",
    var unit: String = ""
)
