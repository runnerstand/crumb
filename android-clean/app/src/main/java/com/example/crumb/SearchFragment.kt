package com.example.crumb

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentSearchBinding
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository = RecipeRepository()
    private val recipeAdapter = RecipeListAdapter(
        onViewClick = ::showRecipeDetails,
        onEditClick = ::editRecipe,
        onDeleteClick = ::confirmDeleteRecipe
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recipeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recipeRecyclerView.adapter = recipeAdapter
        binding.retryRecipesButton.setOnClickListener {
            loadRecipes()
        }
        setFragmentResultListener(RecipeFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadRecipes()
        }
        loadRecipes()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            loadRecipes()
        }
    }

    private fun loadRecipes() {
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getUserRecipes()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipes ->
                    recipeAdapter.submitRecipes(recipes)
                    binding.recipeRecyclerView.visibility =
                        if (recipes.isEmpty()) View.GONE else View.VISIBLE
                    binding.retryRecipesButton.visibility = View.GONE
                    binding.recipeStatusText.visibility = View.VISIBLE
                    binding.recipeStatusText.text = if (recipes.isEmpty()) {
                        getString(R.string.recipes_empty)
                    } else {
                        ""
                    }
                    if (recipes.isNotEmpty()) {
                        binding.recipeStatusText.visibility = View.GONE
                    }
                },
                onFailure = { error ->
                    binding.recipeRecyclerView.visibility = View.GONE
                    binding.retryRecipesButton.visibility = View.VISIBLE
                    binding.recipeStatusText.visibility = View.VISIBLE
                    binding.recipeStatusText.text = error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.recipes_error)
                }
            )
        }
    }

    private fun showLoading() {
        binding.recipeStatusText.visibility = View.VISIBLE
        binding.recipeStatusText.text = getString(R.string.recipes_loading)
        binding.retryRecipesButton.visibility = View.GONE
        binding.recipeRecyclerView.visibility = View.GONE
    }

    private fun showRecipeDetails(recipe: RecipeResponse) {
        val ingredients = recipe.ingredients.joinToString("\n") {
            val measurement = listOfNotNull(it.quantity, it.unit)
                .joinToString(" ")
                .takeIf { value -> value.isNotBlank() }
            if (measurement == null) it.ingredientName else "${it.ingredientName} - $measurement"
        }
        val message = buildString {
            appendLine(recipe.cookingTimeMinutes?.let { "$it minutes" } ?: "No cooking time")
            appendLine()
            appendLine("Ingredients")
            appendLine(ingredients.ifBlank { "No ingredients" })
            appendLine()
            appendLine("Instructions")
            append(recipe.instructions.joinToString("\n"))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(recipe.title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun editRecipe(recipe: RecipeResponse) {
        RecipeFormDialogFragment.newInstance(recipe.id)
            .show(parentFragmentManager, "RecipeFormDialog")
    }

    private fun confirmDeleteRecipe(recipe: RecipeResponse) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_recipe_title)
            .setMessage(R.string.delete_recipe_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteRecipe(recipe)
            }
            .show()
    }

    private fun deleteRecipe(recipe: RecipeResponse) {
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.deleteUserRecipe(recipe.id)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { loadRecipes() },
                onFailure = { error ->
                    binding.recipeRecyclerView.visibility = View.GONE
                    binding.retryRecipesButton.visibility = View.VISIBLE
                    binding.recipeStatusText.visibility = View.VISIBLE
                    binding.recipeStatusText.text = error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.recipes_error)
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
