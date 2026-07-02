package com.example.crumb.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.crumb.ui.screens.create.CreateScreen
import com.example.crumb.ui.screens.home.HomeScreen
import com.example.crumb.ui.screens.profile.ProfileScreen
import com.example.crumb.ui.screens.recipes.RecipesScreen

@Composable
fun CrumbNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = CrumbDestination.Home.route,
        modifier = Modifier.padding(contentPadding)
    ) {
        composable(CrumbDestination.Home.route) {
            HomeScreen()
        }
        composable(CrumbDestination.Create.route) {
            CreateScreen()
        }
        composable(CrumbDestination.Recipes.route) {
            RecipesScreen()
        }
        composable(CrumbDestination.Profile.route) {
            ProfileScreen()
        }
    }
}
