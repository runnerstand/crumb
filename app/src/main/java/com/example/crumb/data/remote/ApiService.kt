package com.example.crumb.data.remote

import com.example.crumb.data.remote.model.CommunityCommentCreateRequest
import com.example.crumb.data.remote.model.CommunityCommentResponse
import com.example.crumb.data.remote.model.CommunityPostCreateRequest
import com.example.crumb.data.remote.model.CommunityPostResponse
import com.example.crumb.data.remote.model.DeleteMessageResponse
import com.example.crumb.data.remote.model.HealthResponse
import com.example.crumb.data.remote.model.IngredientCategoryResponse
import com.example.crumb.data.remote.model.RecipeCreateRequest
import com.example.crumb.data.remote.model.RecipeResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("community/posts")
    suspend fun getCommunityPosts(): List<CommunityPostResponse>

    @GET("ingredients/categories")
    suspend fun getIngredientCategories(): List<IngredientCategoryResponse>

    @GET("recipes/user-created")
    suspend fun getRecipes(): List<RecipeResponse>

    @GET("recipes/user-created/{recipe_id}")
    suspend fun getRecipe(
        @Path("recipe_id") recipeId: Int
    ): RecipeResponse

    @Headers("Content-Type: application/json")
    @POST("recipes/user-created")
    suspend fun createRecipe(
        @Body request: RecipeCreateRequest
    ): RecipeResponse

    @Headers("Content-Type: application/json")
    @PATCH("recipes/user-created/{recipe_id}")
    suspend fun updateRecipe(
        @Path("recipe_id") recipeId: Int,
        @Body request: RecipeCreateRequest
    ): RecipeResponse

    @DELETE("recipes/user-created/{recipe_id}")
    suspend fun deleteRecipe(
        @Path("recipe_id") recipeId: Int
    ): DeleteMessageResponse

    @Headers("Content-Type: application/json")
    @POST("community/posts")
    suspend fun createCommunityPost(
        @Body request: CommunityPostCreateRequest
    ): CommunityPostResponse

    @Headers("Content-Type: application/json")
    @PATCH("community/posts/{post_id}")
    suspend fun updateCommunityPost(
        @Path("post_id") postId: Int,
        @Body request: CommunityPostCreateRequest
    ): CommunityPostResponse

    @DELETE("community/posts/{post_id}")
    suspend fun deleteCommunityPost(
        @Path("post_id") postId: Int
    ): DeleteMessageResponse

    @GET("community/posts/{post_id}/comments")
    suspend fun getCommunityComments(
        @Path("post_id") postId: Int
    ): List<CommunityCommentResponse>

    @POST("community/posts/{post_id}/comments")
    suspend fun createCommunityComment(
        @Path("post_id") postId: Int,
        @Body request: CommunityCommentCreateRequest
    ): CommunityCommentResponse

    @PATCH("community/comments/{comment_id}")
    suspend fun updateCommunityComment(
        @Path("comment_id") commentId: Int,
        @Body request: CommunityCommentCreateRequest
    ): CommunityCommentResponse

    @DELETE("community/comments/{comment_id}")
    suspend fun deleteCommunityComment(
        @Path("comment_id") commentId: Int
    ): DeleteMessageResponse
}
