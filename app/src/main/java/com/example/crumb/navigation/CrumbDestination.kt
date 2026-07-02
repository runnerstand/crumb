package com.example.crumb.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector

sealed class CrumbDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : CrumbDestination("home", "Home", Icons.Filled.Home)
    data object Create : CrumbDestination("create", "Create", Icons.Filled.AddCircle)
    data object Recipes : CrumbDestination("recipes", "Recipes", Icons.Filled.Restaurant)
    data object Profile : CrumbDestination("profile", "Profile", Icons.Filled.Person)
}

val bottomNavDestinations = listOf(
    CrumbDestination.Home,
    CrumbDestination.Create,
    CrumbDestination.Recipes,
    CrumbDestination.Profile
)
