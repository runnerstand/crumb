package com.example.crumb.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.example.crumb.ui.theme.CrumbTheme

@Composable
fun HomeScreen(
    refreshSignal: Long,
    onOpenComments: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal != 0L) {
            viewModel.loadPosts()
        }
    }

    LaunchedEffect(operationMessage) {
        val message = operationMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearOperationMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            onOpenComments = onOpenComments,
            onRetryClick = viewModel::loadPosts,
            onUpdatePost = viewModel::updatePost,
            onDeletePost = viewModel::deletePost,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun HomeContent(
    uiState: CommunityPostUiState,
    onOpenComments: (Int) -> Unit,
    onRetryClick: () -> Unit,
    onUpdatePost: (Int, String, List<String>, String) -> Unit,
    onDeletePost: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Community",
            style = MaterialTheme.typography.headlineMedium
        )

        when (val state = uiState) {
            CommunityPostUiState.Loading -> LoadingContent()
            CommunityPostUiState.Empty -> EmptyContent(onRetryClick = onRetryClick)
            is CommunityPostUiState.Error -> ErrorContent(
                message = state.message,
                onRetryClick = onRetryClick
            )

            is CommunityPostUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                items(state.posts, key = { it.id }) { post ->
                    CommunityPostCard(
                        post = post,
                        onOpenComments = { onOpenComments(post.id) },
                        onUpdatePost = onUpdatePost,
                        onDeletePost = onDeletePost,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No community posts yet.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = onRetryClick,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onRetryClick,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPostUiModel,
    onOpenComments: () -> Unit,
    onUpdatePost: (Int, String, List<String>, String) -> Unit,
    onDeletePost: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember(post.id) { mutableStateOf(false) }
    var title by remember(post.id) { mutableStateOf(post.title) }
    var ingredients by remember(post.id) { mutableStateOf(post.ingredients.joinToString(", ")) }
    var caption by remember(post.id) { mutableStateOf(post.caption) }
    var validationMessage by remember(post.id) { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember(post.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredients") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    supportingText = { Text("${caption.length}/200") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                validationMessage?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = {
                            val cleanedIngredients = ingredients
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            validationMessage = when {
                                title.trim().isBlank() -> "Title is required."
                                cleanedIngredients.isEmpty() -> "Add at least one ingredient."
                                caption.trim().length > 200 -> "Caption must be 200 characters or fewer."
                                else -> null
                            }
                            if (validationMessage == null) {
                                onUpdatePost(post.id, title, cleanedIngredients, caption)
                                isEditing = false
                            }
                        }
                    ) {
                        Text("Save")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { isEditing = false }) {
                        Text("Cancel")
                    }
                }
            } else {
                Text(text = post.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "By ${post.creatorName}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Ingredients: ${post.ingredients.joinToString(", ")}",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (post.caption.isNotBlank()) {
                    Text(
                        text = post.caption,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = "Created ${post.createdAt} | Updated ${post.updatedAt}",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row {
                    TextButton(onClick = onOpenComments) {
                        Text("Comments")
                    }
                    if (post.isOwnedByLocalUser) {
                        TextButton(onClick = { isEditing = true }) {
                            Text("Edit")
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete post?") },
            text = { Text("This will also remove its comments.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePost(post.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreviewLight() {
    CrumbTheme(darkTheme = false) {
        HomeContent(
            uiState = CommunityPostUiState.Success(
                posts = listOf(
                    CommunityPostUiModel(
                        id = 1,
                        creatorId = "1",
                        creatorName = "Chef Alice",
                        title = "Spaghetti Bolognese",
                        ingredients = listOf("Pasta", "Tomato", "Minced Beef", "Garlic"),
                        caption = "An Italian classic made with love.",
                        createdAt = "2026-07-03",
                        updatedAt = "2026-07-03",
                        isOwnedByLocalUser = true
                    ),
                    CommunityPostUiModel(
                        id = 2,
                        creatorId = "2",
                        creatorName = "Baker Bob",
                        title = "Sourdough Bread",
                        ingredients = listOf("Flour", "Water", "Salt", "Sourdough Starter"),
                        caption = "Crispy crust and chewy crumb.",
                        createdAt = "2026-07-02",
                        updatedAt = "2026-07-02",
                        isOwnedByLocalUser = false
                    )
                )
            ),
            onOpenComments = {},
            onRetryClick = {},
            onUpdatePost = { _, _, _, _ -> },
            onDeletePost = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreviewDark() {
    CrumbTheme(darkTheme = true) {
        HomeContent(
            uiState = CommunityPostUiState.Success(
                posts = listOf(
                    CommunityPostUiModel(
                        id = 1,
                        creatorId = "1",
                        creatorName = "Chef Alice",
                        title = "Spaghetti Bolognese",
                        ingredients = listOf("Pasta", "Tomato", "Minced Beef", "Garlic"),
                        caption = "An Italian classic made with love.",
                        createdAt = "2026-07-03",
                        updatedAt = "2026-07-03",
                        isOwnedByLocalUser = true
                    )
                )
            ),
            onOpenComments = {},
            onRetryClick = {},
            onUpdatePost = { _, _, _, _ -> },
            onDeletePost = {}
        )
    }
}
