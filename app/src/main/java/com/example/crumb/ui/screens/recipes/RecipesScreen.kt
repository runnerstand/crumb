package com.example.crumb.ui.screens.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.crumb.ui.theme.CrumbTheme

@Composable
fun RecipesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Recipes",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Browse saved and suggested recipes once recipe data is connected.",
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecipesScreenPreviewLight() {
    CrumbTheme(darkTheme = false) {
        RecipesScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun RecipesScreenPreviewDark() {
    CrumbTheme(darkTheme = true) {
        RecipesScreen()
    }
}
