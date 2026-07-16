package com.example.crumb

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.crumb.data.HealthRepository
import com.example.crumb.databinding.FragmentProfileBinding
import com.google.android.material.chip.Chip
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
        loadPreferences()
        binding.savePreferencesButton.setOnClickListener {
            savePreferences()
        }
        binding.retryButton.setOnClickListener {
            checkBackendHealth()
        }
        checkBackendHealth()
    }

    private fun loadPreferences() {
        val savedDietaryPreferences = profilePreferences()
            .getStringSet(KEY_DIETARY_PREFERENCES, emptySet())
            .orEmpty()
        val savedAvoidIngredients = profilePreferences()
            .getStringSet(KEY_AVOID_INGREDIENTS, emptySet())
            .orEmpty()

        dietaryPreferenceChips().forEach { (value, chip) ->
            chip.isChecked = savedDietaryPreferences.contains(value)
        }
        avoidIngredientChips().forEach { (value, chip) ->
            chip.isChecked = savedAvoidIngredients.contains(value)
        }
        binding.preferencesStatusText.visibility = View.GONE
    }

    private fun savePreferences() {
        binding.savePreferencesButton.isEnabled = false
        val dietaryPreferences = checkedValues(dietaryPreferenceChips())
        val avoidIngredients = checkedValues(avoidIngredientChips())

        profilePreferences()
            .edit()
            .putStringSet(KEY_DIETARY_PREFERENCES, dietaryPreferences)
            .putStringSet(KEY_AVOID_INGREDIENTS, avoidIngredients)
            .apply()

        binding.preferencesStatusText.text = getString(R.string.preferences_saved)
        binding.preferencesStatusText.visibility = View.VISIBLE
        binding.savePreferencesButton.isEnabled = true
    }

    private fun checkedValues(chips: List<Pair<String, Chip>>): Set<String> {
        return chips
            .filter { (_, chip) -> chip.isChecked }
            .map { (value, _) -> value }
            .toSet()
    }

    private fun dietaryPreferenceChips(): List<Pair<String, Chip>> {
        return listOf(
            VALUE_VEGETARIAN to binding.dietaryVegetarianChip,
            VALUE_VEGAN to binding.dietaryVeganChip,
            VALUE_HALAL to binding.dietaryHalalChip,
            VALUE_GLUTEN_FREE to binding.dietaryGlutenFreeChip,
            VALUE_DAIRY_FREE to binding.dietaryDairyFreeChip
        )
    }

    private fun avoidIngredientChips(): List<Pair<String, Chip>> {
        return listOf(
            VALUE_PEANUTS to binding.avoidPeanutsChip,
            VALUE_TREE_NUTS to binding.avoidTreeNutsChip,
            VALUE_SHELLFISH to binding.avoidShellfishChip,
            VALUE_MILK to binding.avoidMilkChip,
            VALUE_EGGS to binding.avoidEggsChip,
            VALUE_SOY to binding.avoidSoyChip
        )
    }

    private fun profilePreferences() =
        requireContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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

    private companion object {
        const val PREFERENCES_NAME = "crumb_profile_preferences"
        const val KEY_DIETARY_PREFERENCES = "dietary_preferences"
        const val KEY_AVOID_INGREDIENTS = "avoid_ingredients"
        const val VALUE_VEGETARIAN = "vegetarian"
        const val VALUE_VEGAN = "vegan"
        const val VALUE_HALAL = "halal"
        const val VALUE_GLUTEN_FREE = "gluten_free"
        const val VALUE_DAIRY_FREE = "dairy_free"
        const val VALUE_PEANUTS = "peanuts"
        const val VALUE_TREE_NUTS = "tree_nuts"
        const val VALUE_SHELLFISH = "shellfish"
        const val VALUE_MILK = "milk"
        const val VALUE_EGGS = "eggs"
        const val VALUE_SOY = "soy"
    }
}
