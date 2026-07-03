package com.example.crumb.ui.screens.create

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.example.crumb.ui.theme.CrumbTheme

@Composable
fun CreateScreen(
    onPostCreated: () -> Unit,
    viewModel: CreatePostViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val catalogueState by viewModel.catalogueState.collectAsState()
    val selectedIngredients by viewModel.selectedIngredients.collectAsState()

    CreateContent(
        uiState = uiState,
        catalogueState = catalogueState,
        selectedIngredients = selectedIngredients,
        onRemoveIngredient = viewModel::removeIngredient,
        onAddIngredient = viewModel::addIngredient,
        onLoadCatalogue = viewModel::loadIngredientCatalogue,
        onCreatePost = viewModel::createPost,
        onPostCreated = onPostCreated,
        resetState = viewModel::resetState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateContent(
    uiState: CreatePostUiState,
    catalogueState: IngredientCatalogueUiState,
    selectedIngredients: List<String>,
    onRemoveIngredient: (String) -> Unit,
    onAddIngredient: (String) -> Unit,
    onLoadCatalogue: () -> Unit,
    onCreatePost: (String, String) -> Unit,
    onPostCreated: () -> Unit,
    resetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var ingredientSearch by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is CreatePostUiState.Success) {
            title = ""
            ingredientSearch = ""
            caption = ""
            resetState()
            onPostCreated()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Create Post",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Post details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Post Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Give your post a delicious title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption") },
                        placeholder = { Text("Share the story behind this recipe...") },
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("${caption.length}/200")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }

            // Ingredients card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (selectedIngredients.isNotEmpty()) {
                        Text(
                            text = "Selected Ingredients (${selectedIngredients.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedIngredients.forEach { ingredient ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemoveIngredient(ingredient) },
                                    label = { Text(ingredient) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $ingredient",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Select at least one supported ingredient.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = ingredientSearch,
                        onValueChange = { ingredientSearch = it },
                        label = { Text("Search ingredients") },
                        placeholder = { Text("Type to search...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (ingredientSearch.isNotEmpty()) {
                                IconButton(onClick = { ingredientSearch = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )

                    when (val state = catalogueState) {
                        IngredientCatalogueUiState.Loading -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Loading ingredients...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        IngredientCatalogueUiState.Empty -> Text(
                            text = "No supported ingredients are available from the backend.",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        is IngredientCatalogueUiState.Error -> Column(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = onLoadCatalogue,
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
                                onAddIngredient(it.name)
                                ingredientSearch = ""
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Error feedback
            when (val state = uiState) {
                is CreatePostUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                else -> Unit
            }

            val isSaving = uiState is CreatePostUiState.Saving

            // Publish button
            Button(
                onClick = {
                    onCreatePost(title, caption)
                },
                enabled = !isSaving && catalogueState is IngredientCatalogueUiState.Success,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Publishing...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        text = "Publish Post",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Text(
                text = "Posts are created as Local User until authentication is added.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPicker(
    ingredients: List<IngredientPickerItem>,
    selectedIngredients: List<String>,
    query: String,
    onIngredientClick: (IngredientPickerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedQuery = query.trim().lowercase()

    // Extract unique categories dynamically
    val categories = remember(ingredients) {
        ingredients.map { it.category }.distinct().sorted()
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val availableIngredients = remember(ingredients, selectedIngredients, normalizedQuery, selectedCategory) {
        ingredients
            .filterNot { ingredient -> selectedIngredients.contains(ingredient.name) }
            .filter { ingredient -> selectedCategory == null || ingredient.category == selectedCategory }
            .filter { ingredient ->
                normalizedQuery.isBlank() ||
                    ingredient.name.contains(normalizedQuery, ignoreCase = true) ||
                    ingredient.category.contains(normalizedQuery, ignoreCase = true)
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (categories.isNotEmpty()) {
            Text(
                text = "Filter by Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                        shape = RoundedCornerShape(50)
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.toDisplayLabel()) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        if (availableIngredients.isEmpty()) {
            Text(
                text = "No matching supported ingredients.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }

        availableIngredients
            .groupBy { it.category }
            .toSortedMap()
            .forEach { (category, categoryIngredients) ->
                Text(
                    text = category.toDisplayLabel(),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryIngredients.forEach { ingredient ->
                        FilterChip(
                            selected = false,
                            onClick = { onIngredientClick(ingredient) },
                            label = { Text(ingredient.name) },
                            shape = RoundedCornerShape(8.dp)
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

@Preview(showBackground = true)
@Composable
fun CreateContentPreviewLight() {
    CrumbTheme(darkTheme = false) {
        CreateContent(
            uiState = CreatePostUiState.Idle,
            catalogueState = IngredientCatalogueUiState.Success(
                ingredients = listOf(
                    IngredientPickerItem("Tomato", "Vegetables", emptyList()),
                    IngredientPickerItem("Potato", "Vegetables", emptyList()),
                    IngredientPickerItem("Beef", "Meats", emptyList()),
                    IngredientPickerItem("Oregano", "Spices", emptyList())
                )
            ),
            selectedIngredients = listOf("Tomato", "Oregano"),
            onRemoveIngredient = {},
            onAddIngredient = {},
            onLoadCatalogue = {},
            onCreatePost = { _, _ -> },
            onPostCreated = {},
            resetState = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CreateContentPreviewDark() {
    CrumbTheme(darkTheme = true) {
        CreateContent(
            uiState = CreatePostUiState.Idle,
            catalogueState = IngredientCatalogueUiState.Success(
                ingredients = listOf(
                    IngredientPickerItem("Tomato", "Vegetables", emptyList()),
                    IngredientPickerItem("Potato", "Vegetables", emptyList()),
                    IngredientPickerItem("Beef", "Meats", emptyList()),
                    IngredientPickerItem("Oregano", "Spices", emptyList())
                )
            ),
            selectedIngredients = listOf("Tomato"),
            onRemoveIngredient = {},
            onAddIngredient = {},
            onLoadCatalogue = {},
            onCreatePost = { _, _ -> },
            onPostCreated = {},
            resetState = {}
        )
    }
}
