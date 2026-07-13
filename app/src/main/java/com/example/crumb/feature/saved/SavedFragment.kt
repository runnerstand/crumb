package com.example.crumb.feature.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.databinding.FragmentSavedBinding
import com.example.crumb.ui.adapter.SavedRecipeAdapter
import com.example.crumb.ui.screens.recipes.SavedRecipeUiModel
import com.example.crumb.ui.screens.recipes.SavedRecipeUiState
import com.example.crumb.ui.screens.saved.SavedRecipesViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SavedFragment : Fragment() {
    private var _binding: FragmentSavedBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: SavedRecipesViewModel by viewModels()
    private lateinit var savedRecipeAdapter: SavedRecipeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedRecipeAdapter = SavedRecipeAdapter(
            onViewRecipe = ::showSavedRecipeDetails,
            onUnsaveRecipe = viewModel::unsaveRecipe
        )
        binding.savedRecipesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.savedRecipesRecyclerView.adapter = savedRecipeAdapter
        binding.savedRecipesRetryButton.setOnClickListener { viewModel.loadSavedRecipes() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderSavedRecipes) }
                launch {
                    viewModel.operationMessage.collect { message ->
                        message ?: return@collect
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearOperationMessage()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSavedRecipes()
    }

    private fun renderSavedRecipes(state: SavedRecipeUiState) = with(binding) {
        savedRecipesProgressBar.visibility =
            if (state is SavedRecipeUiState.Loading) View.VISIBLE else View.GONE
        savedRecipesRecyclerView.visibility =
            if (state is SavedRecipeUiState.Success) View.VISIBLE else View.GONE
        savedRecipesRetryButton.visibility =
            if (state is SavedRecipeUiState.Error) View.VISIBLE else View.GONE
        savedRecipesStatusTextView.visibility =
            if (state is SavedRecipeUiState.Error || state is SavedRecipeUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (state) {
            SavedRecipeUiState.Loading -> savedRecipesStatusTextView.text = ""
            SavedRecipeUiState.Empty -> {
                savedRecipesStatusTextView.text = "No saved recipes yet."
                savedRecipeAdapter.submitList(emptyList())
            }
            is SavedRecipeUiState.Error -> {
                savedRecipesStatusTextView.text = state.message
                savedRecipeAdapter.submitList(emptyList())
            }
            is SavedRecipeUiState.Success -> savedRecipeAdapter.submitList(state.recipes)
        }
    }

    private fun showSavedRecipeDetails(recipe: SavedRecipeUiModel) {
        val details = buildString {
            appendLine("Cooking time: ${recipe.cookingTimeMinutes} minutes")
            appendLine()
            appendLine("Ingredients")
            recipe.ingredients.forEach { appendLine("- $it") }
            if (recipe.missingIngredients.isNotEmpty()) {
                appendLine()
                appendLine("Missing ingredients")
                recipe.missingIngredients.forEach { appendLine("- $it") }
            }
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

    override fun onDestroyView() {
        binding.savedRecipesRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
