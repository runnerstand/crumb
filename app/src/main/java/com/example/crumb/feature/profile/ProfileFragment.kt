package com.example.crumb.feature.profile

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.R
import com.example.crumb.databinding.FragmentProfileBinding
import com.example.crumb.ui.adapter.RecipeAdapter
import com.example.crumb.ui.adapter.SavedRecipeAdapter
import com.example.crumb.ui.screens.profile.ProfileViewModel
import com.example.crumb.ui.screens.profile.ProfileUiState
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.recipes.SavedRecipeUiModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var myRecipeAdapter: RecipeAdapter
    private lateinit var savedRecipeAdapter: SavedRecipeAdapter
    private var latestBackendStatus: String = "Unknown"
    private val preferences by lazy {
        requireContext().getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        myRecipeAdapter = RecipeAdapter(
            onViewRecipe = ::showRecipeDetails,
            onSaveRecipe = {},
            onUnsaveRecipe = {},
            onEditRecipe = {},
            onDeleteRecipe = {},
            showOwnerActions = false,
            showSaveAction = false
        )
        savedRecipeAdapter = SavedRecipeAdapter(
            onViewRecipe = ::showSavedRecipeDetails,
            onUnsaveRecipe = {},
            showUnsaveAction = false
        )
        binding.myRecipesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myRecipesRecyclerView.adapter = myRecipeAdapter
        binding.savedRecipesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.savedRecipesRecyclerView.adapter = savedRecipeAdapter

        binding.healthRetryButton.setOnClickListener { viewModel.loadProfile() }
        binding.seeAllRecipesButton.setOnClickListener {
            findNavController().navigate(R.id.recipesFragment)
        }
        binding.seeAllSavedButton.setOnClickListener {
            findNavController().navigate(R.id.savedFragment)
        }
        binding.addAvoidIngredientButton.setOnClickListener { addAvoidIngredient() }
        binding.settingsRowTextView.setOnClickListener {
            Snackbar.make(binding.root, "Settings are local for now.", Snackbar.LENGTH_SHORT).show()
        }
        binding.aboutRowTextView.setOnClickListener { showAboutDialog() }
        renderDietaryPreferenceChips()
        renderAvoidIngredientChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profileUiState.collect(::renderProfile)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun renderProfile(state: ProfileUiState) = with(binding) {
        healthProgressBar.visibility = if (state is ProfileUiState.Loading) View.VISIBLE else View.GONE
        profileMessageTextView.visibility = if (state is ProfileUiState.Error) View.VISIBLE else View.GONE
        healthRetryButton.visibility = if (state is ProfileUiState.Error) View.VISIBLE else View.GONE

        when (state) {
            ProfileUiState.Loading -> {
                profileMessageTextView.text = ""
            }
            is ProfileUiState.Error -> {
                latestBackendStatus = state.backendStatus
                healthStatusTextView.text = "Backend status: ${state.backendStatus}"
                profileMessageTextView.text = state.message
                myRecipeAdapter.submitList(emptyList())
                savedRecipeAdapter.submitList(emptyList())
            }
            is ProfileUiState.Success -> {
                latestBackendStatus = state.backendStatus
                healthStatusTextView.text = "Backend status: ${state.backendStatus}"
                myRecipesCountTextView.text = "${state.myRecipes.size}\nRecipes"
                savedRecipesCountTextView.text = "${state.savedRecipes.size}\nSaved"
                myPostsCountTextView.text = "${state.myPostsCount}\nPosts"
                renderRecipeList(state.myRecipes)
                renderSavedRecipeList(state.savedRecipes)
            }
        }
    }

    private fun renderRecipeList(recipes: List<RecipeUiModel>) = with(binding) {
        val previewRecipes = recipes.take(PROFILE_LIST_LIMIT)
        myRecipesRecyclerView.visibility = if (previewRecipes.isEmpty()) View.GONE else View.VISIBLE
        myRecipesEmptyTextView.visibility = if (previewRecipes.isEmpty()) View.VISIBLE else View.GONE
        seeAllRecipesButton.visibility =
            if (recipes.size > PROFILE_LIST_LIMIT) View.VISIBLE else View.GONE
        myRecipeAdapter.submitList(previewRecipes)
    }

    private fun renderSavedRecipeList(recipes: List<SavedRecipeUiModel>) = with(binding) {
        val previewRecipes = recipes.take(PROFILE_LIST_LIMIT)
        savedRecipesRecyclerView.visibility =
            if (previewRecipes.isEmpty()) View.GONE else View.VISIBLE
        savedRecipesEmptyTextView.visibility =
            if (previewRecipes.isEmpty()) View.VISIBLE else View.GONE
        seeAllSavedButton.visibility =
            if (recipes.size > PROFILE_LIST_LIMIT) View.VISIBLE else View.GONE
        savedRecipeAdapter.submitList(previewRecipes)
    }

    private fun renderDietaryPreferenceChips() {
        with(binding.dietaryPreferencesChipGroup) {
            removeAllViews()
            val selectedPreferences = preferences.getStringSet(DIETARY_PREFS_KEY, emptySet()).orEmpty()
            DIETARY_OPTIONS.forEach { option ->
                addView(Chip(requireContext()).apply {
                    text = option
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, PROFILE_CHIP_TEXT_SIZE_SP)
                    minHeight = resources.getDimensionPixelSize(R.dimen.profile_chip_min_height)
                    isCheckable = true
                    isChecked = option in selectedPreferences
                    setOnCheckedChangeListener { _, _ -> saveDietaryPreferences() }
                })
            }
        }
    }

    private fun saveDietaryPreferences() {
        val selectedPreferences = mutableSetOf<String>()
        for (index in 0 until binding.dietaryPreferencesChipGroup.childCount) {
            val chip = binding.dietaryPreferencesChipGroup.getChildAt(index) as? Chip ?: continue
            if (chip.isChecked) {
                selectedPreferences.add(chip.text.toString())
            }
        }
        preferences.edit().putStringSet(DIETARY_PREFS_KEY, selectedPreferences).apply()
    }

    private fun renderAvoidIngredientChips() {
        with(binding.avoidIngredientsChipGroup) {
            removeAllViews()
            getAvoidIngredients().forEach { ingredient ->
                addView(Chip(requireContext()).apply {
                    text = ingredient
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, PROFILE_CHIP_TEXT_SIZE_SP)
                    minHeight = resources.getDimensionPixelSize(R.dimen.profile_chip_min_height)
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        saveAvoidIngredients(getAvoidIngredients().filterNot { it == ingredient })
                        renderAvoidIngredientChips()
                    }
                })
            }
        }
    }

    private fun addAvoidIngredient() = with(binding) {
        val ingredient = avoidIngredientEditText.text?.toString()?.trim().orEmpty()
        if (ingredient.isBlank()) {
            return
        }
        val updatedIngredients = (getAvoidIngredients() + ingredient)
            .distinctBy { it.lowercase() }
            .sorted()
        saveAvoidIngredients(updatedIngredients)
        avoidIngredientEditText.setText("")
        renderAvoidIngredientChips()
    }

    private fun getAvoidIngredients(): List<String> {
        return preferences.getStringSet(AVOID_INGREDIENTS_KEY, DEFAULT_AVOID_INGREDIENTS)
            .orEmpty()
            .sorted()
    }

    private fun saveAvoidIngredients(ingredients: List<String>) {
        preferences.edit().putStringSet(AVOID_INGREDIENTS_KEY, ingredients.toSet()).apply()
    }

    private fun showRecipeDetails(recipe: RecipeUiModel) {
        val details = buildString {
            recipe.cookingTimeMinutes?.let { appendLine("Cooking time: $it minutes") }
            appendLine()
            appendLine("Ingredients")
            recipe.ingredients.forEach { appendLine("- ${it.displayText()}") }
            appendLine()
            appendLine("Instructions")
            recipe.instructions.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
        }.trim()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(recipe.title)
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSavedRecipeDetails(recipe: SavedRecipeUiModel) {
        val details = buildString {
            appendLine("Cooking time: ${recipe.cookingTimeMinutes} minutes")
            appendLine()
            appendLine("Ingredients")
            recipe.ingredients.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Instructions")
            recipe.instructions.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
        }.trim()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(recipe.title)
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("About Crumb")
            .setMessage(
                "Crumb is a local cooking companion for recipes, saved meals, and community posts.\n\n" +
                    "Backend status: $latestBackendStatus"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        binding.myRecipesRecyclerView.adapter = null
        binding.savedRecipesRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val PROFILE_PREFS_NAME = "crumb_profile_preferences"
        const val DIETARY_PREFS_KEY = "dietary_preferences"
        const val AVOID_INGREDIENTS_KEY = "avoid_ingredients"
        const val PROFILE_LIST_LIMIT = 3
        const val PROFILE_CHIP_TEXT_SIZE_SP = 14f
        val DIETARY_OPTIONS = listOf(
            "Vegetarian",
            "Vegan",
            "Pescatarian",
            "Gluten-free",
            "Dairy-free",
            "Low-carb"
        )
        val DEFAULT_AVOID_INGREDIENTS = setOf("Peanuts", "Shellfish", "Cilantro")
    }
}
