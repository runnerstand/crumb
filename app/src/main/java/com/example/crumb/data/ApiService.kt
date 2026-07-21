package com.example.crumb.data

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.DELETE
import okhttp3.MultipartBody
import retrofit2.http.PATCH
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("ingredients/categories")
    suspend fun getIngredientCategories(): List<IngredientResponse>

    @GET("community/posts")
    suspend fun getCommunityPosts(): List<CommunityPostResponse>

    @POST("community/posts")
    suspend fun createCommunityPost(@Body request: CommunityPostRequest): CommunityPostResponse

    @PATCH("community/posts/{post_id}")
    suspend fun updateCommunityPost(
        @Path("post_id") postId: Int,
        @Body request: CommunityPostRequest
    ): CommunityPostResponse

    @DELETE("community/posts/{post_id}")
    suspend fun deleteCommunityPost(@Path("post_id") postId: Int): Map<String, String>

    @GET("community/posts/{post_id}/comments")
    suspend fun getCommunityComments(
        @Path("post_id") postId: Int
    ): List<CommunityCommentResponse>

    @POST("community/posts/{post_id}/comments")
    suspend fun createCommunityComment(
        @Path("post_id") postId: Int,
        @Body request: CommunityCommentRequest
    ): CommunityCommentResponse

    @PATCH("community/comments/{comment_id}")
    suspend fun updateCommunityComment(
        @Path("comment_id") commentId: Int,
        @Body request: CommunityCommentRequest
    ): CommunityCommentResponse

    @DELETE("community/comments/{comment_id}")
    suspend fun deleteCommunityComment(
        @Path("comment_id") commentId: Int
    ): Map<String, String>

    @GET("recipes/user-created")
    suspend fun getUserRecipes(): List<RecipeResponse>

    @GET("recipes/{recipe_id}/details")
    suspend fun getRecipe(@Path("recipe_id") recipeId: Int): RecipeResponse

    @POST("recipes/user-created")
    suspend fun createUserRecipe(@Body request: RecipeRequest): RecipeResponse

    @Multipart
    @POST("recipes/images")
    suspend fun uploadRecipeImage(@Part image: MultipartBody.Part): RecipeImageUploadResponse

    @GET("recipes/user-created/{recipe_id}")
    suspend fun getUserRecipe(@Path("recipe_id") recipeId: Int): RecipeResponse

    @PATCH("recipes/user-created/{recipe_id}")
    suspend fun updateUserRecipe(
        @Path("recipe_id") recipeId: Int,
        @Body request: RecipeRequest
    ): RecipeResponse

    @DELETE("recipes/user-created/{recipe_id}")
    suspend fun deleteUserRecipe(@Path("recipe_id") recipeId: Int): Map<String, String>

    @GET("recipes/saved")
    suspend fun getSavedRecipes(): List<SavedRecipeResponse>

    @POST("recipes/saved")
    suspend fun saveRecipe(@Body request: SavedRecipeRequest): SavedRecipeResponse

    @GET("recipes/saved/{recipe_id}")
    suspend fun getSavedRecipeStatus(@Path("recipe_id") recipeId: Int): SavedRecipeStatusResponse

    @DELETE("recipes/saved/{recipe_id}")
    suspend fun deleteSavedRecipe(@Path("recipe_id") recipeId: Int): Map<String, String>
}
