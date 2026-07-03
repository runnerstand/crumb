package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.CommunityCommentCreateRequest
import com.example.crumb.data.remote.model.CommunityCommentResponse

class CommunityCommentRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getComments(postId: Int): List<CommunityCommentResponse> {
        return apiService.getCommunityComments(postId)
    }

    suspend fun createComment(postId: Int, commentText: String): CommunityCommentResponse {
        return apiService.createCommunityComment(
            postId = postId,
            request = CommunityCommentCreateRequest(commentText = commentText)
        )
    }

    suspend fun updateComment(commentId: Int, commentText: String): CommunityCommentResponse {
        return apiService.updateCommunityComment(
            commentId = commentId,
            request = CommunityCommentCreateRequest(commentText = commentText)
        )
    }

    suspend fun deleteComment(commentId: Int) {
        apiService.deleteCommunityComment(commentId)
    }
}
