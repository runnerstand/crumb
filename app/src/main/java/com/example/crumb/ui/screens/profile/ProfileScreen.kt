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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crumb.ui.theme.CrumbTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel()
) {
    val healthUiState by viewModel.healthUiState.collectAsState()

    ProfileContent(
        healthUiState = healthUiState,
        onRetryClick = viewModel::checkBackendHealth
    )
}

@Composable
fun ProfileContent(
    healthUiState: HealthUiState,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium
        )
        BackendStatus(
            healthUiState = healthUiState,
            onRetryClick = onRetryClick
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

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreviewSuccessLight() {
    CrumbTheme(darkTheme = false) {
        ProfileContent(
            healthUiState = HealthUiState.Success("Healthy"),
            onRetryClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreviewSuccessDark() {
    CrumbTheme(darkTheme = true) {
        ProfileContent(
            healthUiState = HealthUiState.Success("Healthy"),
            onRetryClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreviewErrorLight() {
    CrumbTheme(darkTheme = false) {
        ProfileContent(
            healthUiState = HealthUiState.Error("Could not reach backend server on http://10.0.2.2:8000"),
            onRetryClick = {}
        )
    }
}
