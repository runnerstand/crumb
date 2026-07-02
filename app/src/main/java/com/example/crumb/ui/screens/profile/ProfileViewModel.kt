package com.example.crumb.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crumb.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class ProfileViewModel(
    private val healthRepository: HealthRepository = HealthRepository()
) : ViewModel() {
    private val _healthUiState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val healthUiState: StateFlow<HealthUiState> = _healthUiState.asStateFlow()

    init {
        checkBackendHealth()
    }

    fun checkBackendHealth() {
        _healthUiState.value = HealthUiState.Loading

        viewModelScope.launch {
            _healthUiState.value = try {
                val response = healthRepository.checkHealth()
                HealthUiState.Success(status = response.status)
            } catch (exception: IOException) {
                HealthUiState.Error(
                    message = "Could not reach the backend. Make sure FastAPI is running."
                )
            } catch (exception: Exception) {
                HealthUiState.Error(
                    message = exception.message ?: "Backend health check failed."
                )
            }
        }
    }
}
