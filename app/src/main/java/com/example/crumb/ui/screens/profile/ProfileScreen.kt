package com.example.crumb.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel()
) {
    val healthUiState by viewModel.healthUiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Show temporary local user details and preferences here later.",
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        BackendStatus(
            healthUiState = healthUiState,
            onRetryClick = viewModel::checkBackendHealth
        )
    }
}

@Composable
private fun BackendStatus(
    healthUiState: HealthUiState,
    onRetryClick: () -> Unit
) {
    Text(
        text = "Backend connection",
        modifier = Modifier.padding(top = 32.dp),
        style = MaterialTheme.typography.titleMedium
    )

    when (healthUiState) {
        HealthUiState.Loading -> {
            Text(
                text = "Checking FastAPI health...",
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HealthUiState.Success -> {
            Text(
                text = "Connected. Status: ${healthUiState.status}",
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HealthUiState.Error -> {
            Text(
                text = healthUiState.message,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onRetryClick,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(text = "Retry")
            }
        }
    }
}
