package com.example.crumb.feature.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.crumb.databinding.FragmentProfileBinding
import com.example.crumb.ui.screens.profile.HealthUiState
import com.example.crumb.ui.screens.profile.ProfileViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.healthRetryButton.setOnClickListener { viewModel.checkBackendHealth() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.healthUiState.collect(::renderHealth)
            }
        }
    }

    private fun renderHealth(state: HealthUiState) = with(binding) {
        healthProgressBar.visibility = if (state is HealthUiState.Loading) View.VISIBLE else View.GONE
        healthRetryButton.visibility = if (state is HealthUiState.Error) View.VISIBLE else View.GONE
        healthStatusTextView.text = when (state) {
            HealthUiState.Loading -> "Checking FastAPI health..."
            is HealthUiState.Success -> "Connected. Status: ${state.status}"
            is HealthUiState.Error -> state.message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
