package com.example.crumb.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.crumb.ui.screens.comments.CommentsScreen
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
        composable(CrumbDestination.Home.route) { backStackEntry ->
            val refreshSignal by backStackEntry.savedStateHandle
                .getStateFlow("refreshPosts", 0L)
                .collectAsState()

            HomeScreen(
                refreshSignal = refreshSignal,
                onOpenComments = { postId ->
                    navController.navigate(CrumbDestination.Comments.createRoute(postId))
                }
            )
        }
        composable(CrumbDestination.Create.route) {
            CreateScreen(
                onPostCreated = {
                    navController.getBackStackEntry(CrumbDestination.Home.route)
                        .savedStateHandle["refreshPosts"] = System.currentTimeMillis()
                    navController.navigate(CrumbDestination.Home.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(CrumbDestination.Recipes.route) {
            RecipesScreen()
        }
        composable(CrumbDestination.Profile.route) {
            ProfileScreen()
        }
        composable(
            route = CrumbDestination.Comments.route,
            arguments = listOf(navArgument("postId") { type = NavType.IntType })
        ) { backStackEntry ->
            val postId = checkNotNull(backStackEntry.arguments?.getInt("postId"))
            CommentsScreen(
                postId = postId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
