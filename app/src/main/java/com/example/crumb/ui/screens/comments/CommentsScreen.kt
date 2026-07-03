package com.example.crumb.ui.screens.comments

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CommentsScreen(
    postId: Int,
    onBackClick: () -> Unit,
    viewModel: CommentsViewModel = viewModel(
        key = "comments-$postId",
        factory = CommentsViewModelFactory(postId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var newCommentText by remember { mutableStateOf("") }

    LaunchedEffect(operationMessage) {
        val message = operationMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearOperationMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackClick) {
                    Text("Back")
                }
                Text(
                    text = "Comments",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            OutlinedTextField(
                value = newCommentText,
                onValueChange = { newCommentText = it },
                label = { Text("Add a comment") },
                supportingText = { Text("${newCommentText.length}/500") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Button(
                onClick = {
                    viewModel.createComment(newCommentText)
                    newCommentText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Post Comment")
            }

            when (val state = uiState) {
                CommunityCommentUiState.Loading -> LoadingComments()
                CommunityCommentUiState.Empty -> EmptyComments(onRetryClick = viewModel::loadComments)
                is CommunityCommentUiState.Error -> CommentError(
                    message = state.message,
                    onRetryClick = viewModel::loadComments
                )

                is CommunityCommentUiState.Success -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp)
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentCard(
                            comment = comment,
                            onUpdateComment = viewModel::updateComment,
                            onDeleteComment = viewModel::deleteComment,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

private class CommentsViewModelFactory(
    private val postId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CommentsViewModel(postId = postId) as T
    }
}

@Composable
private fun LoadingComments() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyComments(onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No comments yet.", style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onRetryClick,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun CommentError(
    message: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
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
private fun CommentCard(
    comment: CommunityCommentUiModel,
    onUpdateComment: (Int, String) -> Unit,
    onDeleteComment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember(comment.id) { mutableStateOf(false) }
    var commentText by remember(comment.id) { mutableStateOf(comment.commentText) }
    var validationMessage by remember(comment.id) { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember(comment.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Comment") },
                    supportingText = { Text("${commentText.length}/500") },
                    modifier = Modifier.fillMaxWidth(),
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
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    Button(
                        onClick = {
                            validationMessage = when {
                                commentText.trim().isBlank() -> "Comment is required."
                                commentText.trim().length > 500 -> "Comment must be 500 characters or fewer."
                                else -> null
                            }
                            if (validationMessage == null) {
                                onUpdateComment(comment.id, commentText)
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
                Text(
                    text = comment.creatorName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = comment.commentText,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Created ${comment.createdAt} | Updated ${comment.updatedAt}",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                if (comment.isOwnedByLocalUser) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
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
            title = { Text("Delete comment?") },
            text = { Text("This comment will be removed from the post.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteComment(comment.id)
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
