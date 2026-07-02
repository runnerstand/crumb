package com.example.crumb.data.repository

import com.example.crumb.data.remote.ApiService
import com.example.crumb.data.remote.RetrofitClient
import com.example.crumb.data.remote.model.HealthResponse

class HealthRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun checkHealth(): HealthResponse {
        return apiService.getHealth()
    }
}
