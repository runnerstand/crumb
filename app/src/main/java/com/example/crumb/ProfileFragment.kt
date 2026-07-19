package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.HealthRepository
import com.example.crumb.data.RecipeRepository
import com.example.crumb.databinding.FragmentProfileBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val healthRepository = HealthRepository()
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private var healthJob: Job? = null
    private var countJob: Job? = null
    private var savedStatusJob: Job? = null
    private var isRestoringPreferences = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.savePreferencesButton.visibility = View.GONE
        loadPreferences()
        setupPreferenceAutosave()
        binding.retryButton.setOnClickListener {
            checkBackendHealth()
        }
        loadProfileCounts()
        checkBackendHealth()
    }

    private fun loadPreferences() {
        isRestoringPreferences = true
        val savedDietaryPreferences = ProfilePreferenceStore.loadDietaryPreferences(requireContext())
        val savedAvoidIngredients = ProfilePreferenceStore.loadAvoidIngredients(requireContext())

        dietaryPreferenceChips().forEach { (value, chip) ->
            chip.isChecked = savedDietaryPreferences.contains(value)
        }
        avoidIngredientChips().forEach { (value, chip) ->
            chip.isChecked = savedAvoidIngredients.contains(value)
        }
        binding.preferencesStatusText.visibility = View.GONE
        isRestoringPreferences = false
    }

    private fun setupPreferenceAutosave() {
        (dietaryPreferenceChips() + avoidIngredientChips()).forEach { (_, chip) ->
            chip.setOnCheckedChangeListener { _, _ ->
                if (!isRestoringPreferences) {
                    savePreferences()
                }
            }
        }
    }

    private fun savePreferences() {
        val dietaryPreferences = checkedValues(dietaryPreferenceChips())
        val avoidIngredients = checkedValues(avoidIngredientChips())

        val savedDietaryPreferences = ProfilePreferenceStore.loadDietaryPreferences(requireContext())
        val savedAvoidIngredients = ProfilePreferenceStore.loadAvoidIngredients(requireContext())

        if (dietaryPreferences == savedDietaryPreferences && avoidIngredients == savedAvoidIngredients) {
            return
        }

        ProfilePreferenceStore.savePreferences(requireContext(), dietaryPreferences, avoidIngredients)

        showPreferencesSaved()
    }

    private fun showPreferencesSaved() {
        savedStatusJob?.cancel()
        binding.preferencesStatusText.text = getString(R.string.preferences_saved)
        binding.preferencesStatusText.visibility = View.VISIBLE
        savedStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(PREFERENCES_SAVED_MESSAGE_MILLIS)
            if (_binding != null) {
                binding.preferencesStatusText.visibility = View.GONE
            }
        }
    }

    private fun checkedValues(chips: List<Pair<String, Chip>>): Set<String> {
        return chips
            .filter { (_, chip) -> chip.isChecked }
            .map { (value, _) -> value }
            .toSet()
    }

    private fun dietaryPreferenceChips(): List<Pair<String, Chip>> {
        return listOf(
            ProfilePreferenceStore.VALUE_VEGETARIAN to binding.dietaryVegetarianChip
        )
    }

    private fun avoidIngredientChips(): List<Pair<String, Chip>> {
        return listOf(
            ProfilePreferenceStore.VALUE_PEANUTS to binding.avoidPeanutsChip,
            ProfilePreferenceStore.VALUE_TREE_NUTS to binding.avoidTreeNutsChip,
            ProfilePreferenceStore.VALUE_SHELLFISH to binding.avoidShellfishChip,
            ProfilePreferenceStore.VALUE_MILK to binding.avoidMilkChip,
            ProfilePreferenceStore.VALUE_EGGS to binding.avoidEggsChip,
            ProfilePreferenceStore.VALUE_SOY to binding.avoidSoyChip
        )
    }

    private fun loadProfileCounts() {
        countJob?.cancel()
        binding.profileRecipeCount.text = getString(R.string.profile_count_loading)
        binding.profilePostCount.text = getString(R.string.profile_count_loading)

        countJob = viewLifecycleOwner.lifecycleScope.launch {
            val recipesDeferred = async { recipeRepository.getUserRecipes() }
            val postsDeferred = async { postRepository.getCommunityPosts() }

            val recipeResult = recipesDeferred.await()
            val postResult = postsDeferred.await()
            if (_binding == null) return@launch

            binding.profileRecipeCount.text = recipeResult.fold(
                onSuccess = { recipes -> recipes.size.toString() },
                onFailure = { getString(R.string.profile_count_unavailable) }
            )
            binding.profilePostCount.text = postResult.fold(
                onSuccess = { posts -> posts.count { it.creatorId == LOCAL_USER_ID }.toString() },
                onFailure = { getString(R.string.profile_count_unavailable) }
            )
        }
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
        savedStatusJob?.cancel()
        healthJob?.cancel()
        countJob?.cancel()
        savedStatusJob = null
        healthJob = null
        countJob = null
        _binding = null
    }

    private companion object {
        const val LOCAL_USER_ID = "local-user"
        const val PREFERENCES_SAVED_MESSAGE_MILLIS = 1500L
    }
}
