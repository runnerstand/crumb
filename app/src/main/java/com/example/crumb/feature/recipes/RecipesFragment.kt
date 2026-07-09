package com.example.crumb.feature.recipes

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.databinding.DialogRecipeFormBinding
import com.example.crumb.databinding.FragmentRecipesBinding
import com.example.crumb.ui.adapter.EditableRecipeIngredientAdapter
import com.example.crumb.ui.adapter.IngredientAdapter
import com.example.crumb.ui.adapter.RecipeAdapter
import com.example.crumb.ui.screens.create.IngredientCatalogueUiState
import com.example.crumb.ui.screens.create.IngredientPickerItem
import com.example.crumb.ui.screens.recipes.EditableRecipeIngredient
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.recipes.RecipeUiState
import com.example.crumb.ui.screens.recipes.RecipesViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RecipesFragment : Fragment() {
    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: RecipesViewModel by viewModels()
    private lateinit var recipeAdapter: RecipeAdapter
    private var recipeFormDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recipeAdapter = RecipeAdapter(
            onViewRecipe = ::showRecipeDetails,
            onEditRecipe = ::showEditRecipeDialog,
            onDeleteRecipe = ::confirmDeleteRecipe
        )
        binding.recipesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recipesRecyclerView.adapter = recipeAdapter
        binding.retryButton.setOnClickListener { viewModel.loadRecipes() }
        binding.addRecipeButton.setOnClickListener { showCreateRecipeDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderRecipes) }
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
        viewModel.loadRecipes()
    }

    private fun renderRecipes(state: RecipeUiState) = with(binding) {
        progressBar.visibility = if (state is RecipeUiState.Loading) View.VISIBLE else View.GONE
        recipesRecyclerView.visibility = if (state is RecipeUiState.Success) View.VISIBLE else View.GONE
        statusTextView.visibility =
            if (state is RecipeUiState.Error || state is RecipeUiState.Empty) View.VISIBLE else View.GONE
        retryButton.visibility = if (state is RecipeUiState.Error) View.VISIBLE else View.GONE

        when (state) {
            RecipeUiState.Loading -> statusTextView.text = ""
            RecipeUiState.Empty -> {
                statusTextView.text = "No recipes yet. Create your first recipe."
                recipeAdapter.submitList(emptyList())
            }
            is RecipeUiState.Error -> {
                statusTextView.text = state.message
                recipeAdapter.submitList(emptyList())
            }
            is RecipeUiState.Success -> recipeAdapter.submitList(state.recipes)
        }
    }

    private fun showCreateRecipeDialog() {
        showRecipeFormDialog(recipe = null)
    }

    private fun showEditRecipeDialog(recipe: RecipeUiModel) {
        showRecipeFormDialog(recipe)
    }

    private fun showRecipeFormDialog(recipe: RecipeUiModel?) {
        recipeFormDialog?.dismiss()
        val dialogBinding = DialogRecipeFormBinding.inflate(layoutInflater)
        val selectedIngredientAdapter = EditableRecipeIngredientAdapter { }
        val catalogueAdapter = IngredientAdapter { ingredient ->
            selectedIngredientAdapter.addIngredient(ingredient.name)
        }

        dialogBinding.selectedIngredientsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.selectedIngredientsRecyclerView.adapter = selectedIngredientAdapter
        dialogBinding.catalogueRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.catalogueRecyclerView.adapter = catalogueAdapter

        if (recipe != null) {
            dialogBinding.titleEditText.setText(recipe.title)
            dialogBinding.cookingTimeEditText.setText(recipe.cookingTimeMinutes?.toString().orEmpty())
            dialogBinding.instructionsEditText.setText(recipe.instructions.joinToString("\n"))
            selectedIngredientAdapter.submitIngredients(
                recipe.ingredients.map {
                    EditableRecipeIngredient(
                        ingredientName = it.ingredientName,
                        quantity = it.quantity.orEmpty(),
                        unit = it.unit.orEmpty()
                    )
                }
            )
        }

        renderCatalogueForDialog(
            state = viewModel.catalogueState.value,
            binding = dialogBinding,
            adapter = catalogueAdapter
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (recipe == null) "New recipe" else "Edit recipe")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val validation = validateAndSaveRecipe(
                    recipe = recipe,
                    title = dialogBinding.titleEditText.text.toString(),
                    cookingTimeText = dialogBinding.cookingTimeEditText.text.toString(),
                    instructionsText = dialogBinding.instructionsEditText.text.toString(),
                    ingredients = selectedIngredientAdapter.currentIngredients()
                )
                if (validation == null) {
                    dialog.dismiss()
                } else {
                    dialogBinding.validationTextView.text = validation
                }
            }
        }
        dialog.setOnDismissListener {
            recipeFormDialog = null
            dialogBinding.selectedIngredientsRecyclerView.adapter = null
            dialogBinding.catalogueRecyclerView.adapter = null
        }
        recipeFormDialog = dialog
        dialog.show()
    }

    private fun renderCatalogueForDialog(
        state: IngredientCatalogueUiState,
        binding: DialogRecipeFormBinding,
        adapter: IngredientAdapter
    ) {
        when (state) {
            IngredientCatalogueUiState.Loading -> {
                binding.catalogueStatusTextView.text = "Loading ingredients..."
                adapter.submitList(emptyList())
            }
            IngredientCatalogueUiState.Empty -> {
                binding.catalogueStatusTextView.text = "No supported ingredients are available."
                adapter.submitList(emptyList())
            }
            is IngredientCatalogueUiState.Error -> {
                binding.catalogueStatusTextView.text = state.message
                adapter.submitList(emptyList())
            }
            is IngredientCatalogueUiState.Success -> {
                binding.catalogueStatusTextView.text = "Tap an ingredient to add it."
                adapter.submitList(state.ingredients)
            }
        }
    }

    private fun validateAndSaveRecipe(
        recipe: RecipeUiModel?,
        title: String,
        cookingTimeText: String,
        instructionsText: String,
        ingredients: List<EditableRecipeIngredient>
    ): String? {
        val cleanedTitle = title.trim()
        val cleanedInstructions = instructionsText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val cookingTime = cookingTimeText.trim().ifBlank { null }?.toIntOrNull()

        return when {
            cleanedTitle.isBlank() -> "Title is required."
            ingredients.isEmpty() -> "Add at least one ingredient."
            cleanedInstructions.isEmpty() -> "Add at least one instruction."
            cookingTimeText.isNotBlank() && cookingTime == null -> "Cooking time must be a number."
            else -> {
                val requests = ingredients.map { it.toRequest() }
                if (recipe == null) {
                    viewModel.createRecipe(
                        title = cleanedTitle,
                        ingredients = requests,
                        instructions = cleanedInstructions,
                        cookingTimeMinutes = cookingTime
                    )
                } else {
                    viewModel.updateRecipe(
                        recipeId = recipe.id,
                        title = cleanedTitle,
                        ingredients = requests,
                        instructions = cleanedInstructions,
                        cookingTimeMinutes = cookingTime
                    )
                }
                null
            }
        }
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

    private fun confirmDeleteRecipe(recipe: RecipeUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete recipe?")
            .setMessage("This recipe will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteRecipe(recipe.id) }
            .show()
    }

    override fun onDestroyView() {
        recipeFormDialog?.dismiss()
        recipeFormDialog = null
        binding.recipesRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
