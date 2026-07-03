package com.example.crumb.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onPostCreated: () -> Unit,
    viewModel: CreatePostViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val catalogueState by viewModel.catalogueState.collectAsState()
    val selectedIngredients by viewModel.selectedIngredients.collectAsState()
    var title by remember { mutableStateOf("") }
    var ingredientSearch by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is CreatePostUiState.Success) {
            title = ""
            ingredientSearch = ""
            caption = ""
            viewModel.resetState()
            onPostCreated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Create Post",
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Text(
            text = "Ingredients",
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleMedium
        )
        if (selectedIngredients.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedIngredients.forEach { ingredient ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeIngredient(ingredient) },
                        label = { Text(ingredient) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $ingredient"
                            )
                        }
                    )
                }
            }
        } else {
            Text(
                text = "Select at least one supported ingredient.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = ingredientSearch,
            onValueChange = { ingredientSearch = it },
            label = { Text("Search ingredients") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        when (val state = catalogueState) {
            IngredientCatalogueUiState.Loading -> Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading ingredients...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IngredientCatalogueUiState.Empty -> Text(
                text = "No supported ingredients are available from the backend.",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            is IngredientCatalogueUiState.Error -> Column(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = viewModel::loadIngredientCatalogue,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Retry")
                }
            }

            is IngredientCatalogueUiState.Success -> IngredientPicker(
                ingredients = state.ingredients,
                selectedIngredients = selectedIngredients,
                query = ingredientSearch,
                onIngredientClick = {
                    viewModel.addIngredient(it.name)
                    ingredientSearch = ""
                },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Caption") },
            supportingText = { Text("${caption.length}/200") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        when (val state = uiState) {
            is CreatePostUiState.Error -> Text(
                text = state.message,
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            CreatePostUiState.Saving -> CircularProgressIndicator(
                modifier = Modifier.padding(top = 16.dp)
            )

            CreatePostUiState.Idle,
            CreatePostUiState.Success -> Unit
        }

        Button(
            onClick = {
                viewModel.createPost(
                    title = title,
                    caption = caption
                )
            },
            enabled = uiState !is CreatePostUiState.Saving &&
                catalogueState is IngredientCatalogueUiState.Success,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text(text = "Publish")
        }
        Text(
            text = "Posts are created as Local User until authentication is added.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientPicker(
    ingredients: List<IngredientPickerItem>,
    selectedIngredients: List<String>,
    query: String,
    onIngredientClick: (IngredientPickerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedQuery = query.trim().lowercase()
    val availableIngredients = remember(ingredients, selectedIngredients, normalizedQuery) {
        ingredients
            .filterNot { ingredient -> selectedIngredients.contains(ingredient.name) }
            .filter { ingredient ->
                normalizedQuery.isBlank() ||
                    ingredient.name.contains(normalizedQuery, ignoreCase = true) ||
                    ingredient.category.contains(normalizedQuery, ignoreCase = true)
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (availableIngredients.isEmpty()) {
            Text(
                text = "No matching supported ingredients.",
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }

        availableIngredients
            .groupBy { it.category }
            .toSortedMap()
            .forEach { (category, categoryIngredients) ->
                Text(
                    text = category.toDisplayLabel(),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryIngredients.forEach { ingredient ->
                        FilterChip(
                            selected = false,
                            onClick = { onIngredientClick(ingredient) },
                            label = { Text(ingredient.name) }
                        )
                    }
                }
            }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun String.toDisplayLabel(): String {
    return split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
        .ifBlank { "Uncategorized" }
}
