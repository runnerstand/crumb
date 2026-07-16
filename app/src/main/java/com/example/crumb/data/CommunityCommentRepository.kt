package com.example.crumb.data

class CommunityCommentRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getCommunityComments(postId: Int): Result<List<CommunityCommentResponse>> {
        return safeApiCall {
            apiService.getCommunityComments(postId)
        }
    }

    suspend fun createCommunityComment(
        postId: Int,
        request: CommunityCommentRequest
    ): Result<CommunityCommentResponse> {
        return safeApiCall {
            apiService.createCommunityComment(postId, request)
        }
    }

    suspend fun updateCommunityComment(
        commentId: Int,
        request: CommunityCommentRequest
    ): Result<CommunityCommentResponse> {
        return safeApiCall {
            apiService.updateCommunityComment(commentId, request)
        }
    }

    suspend fun deleteCommunityComment(commentId: Int): Result<Unit> {
        return safeApiCall {
            apiService.deleteCommunityComment(commentId)
            Unit
        }
    }
}
