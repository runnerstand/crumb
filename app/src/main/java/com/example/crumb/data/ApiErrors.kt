package com.example.crumb.data

import android.util.Log
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

class UserVisibleApiException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null
) : Exception(message, cause)

suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Log.w("CrumbApi", "API request failed", error)
        Result.failure(UserVisibleApiException(error.toUserVisibleMessage(), error, error.httpStatusCode()))
    }
}

private fun Throwable.httpStatusCode(): Int? {
    return (this as? HttpException)?.code()
}

private fun Throwable.toUserVisibleMessage(): String {
    return when (this) {
        is SocketTimeoutException -> "Server took too long to respond. Please try again."
        is JsonDataException,
        is JsonEncodingException,
        is EOFException,
        is NullPointerException -> "Something went wrong. Please try again."
        is HttpException -> when (code()) {
            in 400..499 -> "Please check the request and try again."
            in 500..599 -> "Something went wrong. Please try again."
            else -> "Something went wrong. Please try again."
        }
        is IOException -> "Unable to connect to server."
        else -> "Something went wrong. Please try again."
    }
}
