package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.CommunityPostCreateRequest
import com.example.crumb.data.remote.model.CommunityPostResponse

class CommunityPostRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getPosts(): List<CommunityPostResponse> {
        return apiService.getCommunityPosts()
    }

    suspend fun createPost(
        title: String,
        ingredients: List<String>,
        caption: String,
        recipeId: Int?
    ): CommunityPostResponse {
        return apiService.createCommunityPost(
            CommunityPostCreateRequest(
                title = title,
                ingredients = ingredients,
                caption = caption,
                recipeId = recipeId
            )
        )
    }

    suspend fun updatePost(
        postId: Int,
        title: String,
        ingredients: List<String>,
        caption: String,
        recipeId: Int?
    ): CommunityPostResponse {
        return apiService.updateCommunityPost(
            postId = postId,
            request = CommunityPostCreateRequest(
                title = title,
                ingredients = ingredients,
                caption = caption,
                recipeId = recipeId
            )
        )
    }

    suspend fun deletePost(postId: Int) {
        apiService.deleteCommunityPost(postId)
    }
}
