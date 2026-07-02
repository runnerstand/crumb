package com.example.crumb

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.example.crumb.navigation.CrumbBottomBar
import com.example.crumb.navigation.CrumbNavHost

@Composable
fun CrumbApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            CrumbBottomBar(navController = navController)
        }
    ) { innerPadding ->
        CrumbNavHost(
            navController = navController,
            contentPadding = innerPadding
        )
    }
}
