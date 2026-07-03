package com.example.crumb.ui.screens.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.CommunityCommentRepository
import com.example.crumb.ui.screens.home.toOperationMessage
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CommentsViewModel(
    private val postId: Int,
    private val repository: CommunityCommentRepository = CommunityCommentRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<CommunityCommentUiState>(CommunityCommentUiState.Loading)
    val uiState: StateFlow<CommunityCommentUiState> = _uiState.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        loadComments()
    }

    fun loadComments() {
        _uiState.value = CommunityCommentUiState.Loading

        viewModelScope.launch {
            _uiState.value = try {
                val comments = repository.getComments(postId).map { it.toUiModel() }
                if (comments.isEmpty()) {
                    CommunityCommentUiState.Empty
                } else {
                    CommunityCommentUiState.Success(comments)
                }
            } catch (exception: IOException) {
                CommunityCommentUiState.Error("Could not reach the backend. Make sure FastAPI is running.")
            } catch (exception: HttpException) {
                CommunityCommentUiState.Error("Backend error ${exception.code()}: ${exception.message()}")
            } catch (exception: Exception) {
                CommunityCommentUiState.Error(exception.message ?: "Could not load comments.")
            }
        }
    }

    fun createComment(commentText: String) {
        val cleanedText = commentText.trim()
        if (cleanedText.isBlank()) {
            _operationMessage.value = "Comment is required."
            return
        }
        if (cleanedText.length > 500) {
            _operationMessage.value = "Comment must be 500 characters or fewer."
            return
        }

        viewModelScope.launch {
            try {
                repository.createComment(postId, cleanedText)
                _operationMessage.value = "Comment added."
                loadComments()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not add comment.")
            }
        }
    }

    fun updateComment(commentId: Int, commentText: String) {
        val cleanedText = commentText.trim()
        if (cleanedText.isBlank()) {
            _operationMessage.value = "Comment is required."
            return
        }
        if (cleanedText.length > 500) {
            _operationMessage.value = "Comment must be 500 characters or fewer."
            return
        }

        viewModelScope.launch {
            try {
                repository.updateComment(commentId, cleanedText)
                _operationMessage.value = "Comment updated."
                loadComments()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not update comment.")
            }
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteComment(commentId)
                _operationMessage.value = "Comment deleted."
                loadComments()
            } catch (exception: Exception) {
                _operationMessage.value = exception.toOperationMessage("Could not delete comment.")
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }
}
