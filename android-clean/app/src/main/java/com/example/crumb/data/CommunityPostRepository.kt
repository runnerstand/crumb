package com.example.crumb.data

class CommunityPostRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getCommunityPosts(): Result<List<CommunityPostResponse>> {
        return safeApiCall {
            apiService.getCommunityPosts()
        }
    }

    suspend fun createCommunityPost(
        request: CommunityPostRequest
    ): Result<CommunityPostResponse> {
        return safeApiCall {
            apiService.createCommunityPost(request)
        }
    }

    suspend fun updateCommunityPost(
        postId: Int,
        request: CommunityPostRequest
    ): Result<CommunityPostResponse> {
        return safeApiCall {
            apiService.updateCommunityPost(postId, request)
        }
    }

    suspend fun deleteCommunityPost(postId: Int): Result<Unit> {
        return safeApiCall {
            apiService.deleteCommunityPost(postId)
            Unit
        }
    }
}
