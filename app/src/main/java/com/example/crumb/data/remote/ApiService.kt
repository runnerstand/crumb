package com.example.crumb.data.remote

import com.example.crumb.data.remote.model.HealthResponse
import retrofit2.http.GET

interface ApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse
}
