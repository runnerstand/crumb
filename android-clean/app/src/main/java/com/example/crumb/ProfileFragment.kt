package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.crumb.data.HealthRepository
import com.example.crumb.databinding.FragmentProfileBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val healthRepository = HealthRepository()
    private var healthJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.retryButton.setOnClickListener {
            checkBackendHealth()
        }
        checkBackendHealth()
    }

    private fun checkBackendHealth() {
        if (healthJob?.isActive == true) {
            return
        }

        binding.backendStatusValue.text = getString(R.string.backend_status_checking)
        binding.retryButton.isEnabled = false

        healthJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = healthRepository.checkHealth()
            if (_binding == null) return@launch

            binding.backendStatusValue.text = result.fold(
                onSuccess = { getString(R.string.backend_status_connected) },
                onFailure = { error ->
                    error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.server_unavailable)
                }
            )
            binding.retryButton.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
