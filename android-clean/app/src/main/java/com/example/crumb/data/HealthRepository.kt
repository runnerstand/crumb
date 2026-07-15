package com.example.crumb.data

class HealthRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) {
    suspend fun checkHealth(): Result<HealthResponse> {
        return runCatching {
            apiService.getHealth()
        }
    }
}
