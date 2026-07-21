package com.example.crumb.data

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RecipeRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun getUserRecipes(): Result<List<RecipeResponse>> {
        return safeApiCall {
            apiService.getUserRecipes()
        }
    }

    suspend fun getRecipe(recipeId: Int): Result<RecipeResponse> {
        return safeApiCall {
            apiService.getRecipe(recipeId)
        }
    }

    suspend fun getUserRecipe(recipeId: Int): Result<RecipeResponse> {
        return safeApiCall {
            apiService.getUserRecipe(recipeId)
        }
    }

    suspend fun createUserRecipe(request: RecipeRequest): Result<RecipeResponse> {
        return safeApiCall {
            apiService.createUserRecipe(request)
        }
    }

    suspend fun uploadRecipeImage(context: Context, imageUri: Uri): Result<RecipeImageUploadResponse> {
        val bytes = try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return Result.failure(UserVisibleApiException("Could not read selected image."))
        } catch (error: Exception) {
            return Result.failure(UserVisibleApiException("Could not read selected image.", error))
        }

        if (bytes.size > MAX_RECIPE_IMAGE_BYTES) {
            return Result.failure(UserVisibleApiException("Image must be 5 MB or smaller."))
        }

        val contentType = detectSupportedImageContentType(bytes)
            ?: return Result.failure(UserVisibleApiException("Only JPEG, PNG, and WebP images are supported."))

        return safeApiCall {
            val requestBody = bytes.toRequestBody(contentType.toMediaType())
            val part = MultipartBody.Part.createFormData(
                "image",
                "recipe-image",
                requestBody
            )
            apiService.uploadRecipeImage(part)
        }
    }

    suspend fun updateUserRecipe(recipeId: Int, request: RecipeRequest): Result<RecipeResponse> {
        return safeApiCall {
            apiService.updateUserRecipe(recipeId, request)
        }
    }

    suspend fun deleteUserRecipe(recipeId: Int): Result<Unit> {
        return safeApiCall {
            apiService.deleteUserRecipe(recipeId)
            Unit
        }
    }

    private fun detectSupportedImageContentType(bytes: ByteArray): String? {
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() &&
                bytes[4] == 0x0D.toByte() &&
                bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0x0A.toByte() -> "image/png"
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> "image/webp"
            else -> null
        }
    }

    private companion object {
        const val MAX_RECIPE_IMAGE_BYTES = 5 * 1024 * 1024
    }
}
