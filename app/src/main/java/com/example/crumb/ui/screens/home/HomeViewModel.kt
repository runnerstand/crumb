package com.example.crumb.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.CommunityPostRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeViewModel(
    private val repository: CommunityPostRepository = CommunityPostRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<CommunityPostUiState>(CommunityPostUiState.Loading)
    val uiState: StateFlow<CommunityPostUiState> = _uiState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts() {
        _uiState.value = CommunityPostUiState.Loading

        viewModelScope.launch {
            _uiState.value = try {
                val posts = repository.getPosts().map { it.toUiModel() }
                if (posts.isEmpty()) {
                    CommunityPostUiState.Empty
                } else {
                    CommunityPostUiState.Success(posts)
                }
            } catch (exception: IOException) {
                CommunityPostUiState.Error("Could not reach the backend. Make sure FastAPI is running.")
            } catch (exception: HttpException) {
                CommunityPostUiState.Error(exception.toOperationMessage("Could not load community posts."))
            } catch (exception: Exception) {
                CommunityPostUiState.Error(exception.message ?: "Could not load community posts.")
            }
        }
    }

    fun updatePost(
        postId: Int,
        title: String,
        ingredients: List<String>,
        caption: String
    ) {
        viewModelScope.launch {
            try {
                repository.updatePost(
                    postId = postId,
                    title = title.trim(),
                    ingredients = ingredients.map { it.trim() },
                    caption = caption.trim()
                )
                _operationMessage.value = "Post updated."
                loadPosts()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not update post.")
            }
        }
    }

    fun deletePost(postId: Int) {
        viewModelScope.launch {
            try {
                repository.deletePost(postId)
                _operationMessage.value = "Post deleted."
                loadPosts()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not delete post.")
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }
}

fun Exception.toOperationMessage(fallback: String): String {
    return when (this) {
        is IOException -> "Could not reach the backend. Make sure FastAPI is running."
        is HttpException -> {
            val errorBody = response()?.errorBody()?.string()?.trim()
            if (errorBody.isNullOrBlank()) {
                "Backend error ${code()}: ${message()}"
            } else {
                "Backend error ${code()}: $errorBody"
            }
        }
        else -> message ?: fallback
    }
}
