package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.IngredientRepository
import com.example.crumb.data.IngredientResponse
import com.example.crumb.data.RecipeIngredientRequest
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeRequest
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.DialogRecipeFormBinding
import kotlinx.coroutines.launch
import java.util.Locale

class RecipeFormDialogFragment : DialogFragment() {
    private var _binding: DialogRecipeFormBinding? = null
    private val binding get() = _binding!!
    private val ingredientRepository = IngredientRepository()
    private val recipeRepository = RecipeRepository()
    private val ingredientAdapter = IngredientCategoryAdapter(::selectIngredient)
    private val selectedIngredientAdapter = SelectedRecipeIngredientAdapter(::removeIngredient)
    private val allIngredients = mutableListOf<IngredientResponse>()
    private val selectedIngredients = linkedMapOf<String, SelectedRecipeIngredient>()
    private val recipeId: Int? by lazy {
        arguments?.getInt(ARG_RECIPE_ID)?.takeIf { it > 0 }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRecipeFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.formTitle.text = if (recipeId == null) {
            getString(R.string.create_recipe)
        } else {
            getString(R.string.edit)
        }
        binding.ingredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ingredientRecyclerView.adapter = ingredientAdapter
        binding.selectedIngredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedIngredientRecyclerView.adapter = selectedIngredientAdapter
        binding.ingredientSearchEditText.doAfterTextChanged {
            updateIngredientList()
        }
        binding.ingredientRetryButton.setOnClickListener {
            loadIngredients()
        }
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.saveButton.setOnClickListener {
            saveRecipe()
        }

        loadIngredients()
        recipeId?.let { loadRecipe(it) }
        updateSelectedIngredients()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadIngredients() {
        binding.ingredientStatusText.text = getString(R.string.ingredients_loading)
        binding.ingredientStatusText.visibility = View.VISIBLE
        binding.ingredientRetryButton.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = ingredientRepository.getIngredientCategories()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { ingredients ->
                    allIngredients.clear()
                    allIngredients += ingredients
                    updateIngredientList()
                },
                onFailure = { error ->
                    binding.ingredientStatusText.text = error.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.ingredients_error)
                    binding.ingredientRetryButton.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun loadRecipe(recipeId: Int) {
        setSavingState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getUserRecipe(recipeId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipe ->
                    populateRecipe(recipe)
                    setSavingState(false)
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.recipes_error))
                    setSavingState(false)
                }
            )
        }
    }

    private fun populateRecipe(recipe: RecipeResponse) {
        binding.recipeTitleEditText.setText(recipe.title)
        binding.cookingTimeEditText.setText(recipe.cookingTimeMinutes?.toString().orEmpty())
        binding.instructionsEditText.setText(recipe.instructions.joinToString("\n"))
        selectedIngredients.clear()
        recipe.ingredients.forEach { ingredient ->
            val selected = SelectedRecipeIngredient(
                name = ingredient.ingredientName,
                quantity = ingredient.quantity.orEmpty(),
                unit = ingredient.unit.orEmpty()
            )
            selectedIngredients[selected.name.lowercase(Locale.US)] = selected
        }
        updateSelectedIngredients()
    }

    private fun updateIngredientList() {
        val query = binding.ingredientSearchEditText.text?.toString().orEmpty()
        ingredientAdapter.submitIngredients(allIngredients, query)
        binding.ingredientStatusText.visibility =
            if (ingredientAdapter.itemCount == 0) View.VISIBLE else View.GONE
        if (ingredientAdapter.itemCount == 0 && allIngredients.isNotEmpty()) {
            binding.ingredientStatusText.text = getString(R.string.ingredients_no_matches)
        }
    }

    private fun selectIngredient(ingredient: IngredientResponse) {
        val key = ingredient.name.lowercase(Locale.US)
        if (selectedIngredients.containsKey(key)) return

        selectedIngredients[key] = SelectedRecipeIngredient(name = ingredient.name)
        updateSelectedIngredients()
    }

    private fun removeIngredient(ingredient: SelectedRecipeIngredient) {
        selectedIngredients.remove(ingredient.name.lowercase(Locale.US))
        updateSelectedIngredients()
    }

    private fun updateSelectedIngredients() {
        selectedIngredientAdapter.submitIngredients(selectedIngredients.values.toList())
        binding.selectedEmptyText.visibility =
            if (selectedIngredients.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveRecipe() {
        val request = buildRecipeRequest() ?: run {
            showFormError(getString(R.string.recipe_form_error))
            return
        }

        setSavingState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeId?.let {
                recipeRepository.updateUserRecipe(it, request)
            } ?: recipeRepository.createUserRecipe(request)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CHANGED to true))
                    dismiss()
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.recipes_error))
                    setSavingState(false)
                }
            )
        }
    }

    private fun buildRecipeRequest(): RecipeRequest? {
        val title = binding.recipeTitleEditText.text?.toString()?.trim().orEmpty()
        val instructions = binding.instructionsEditText.text?.toString()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val cookingTime = binding.cookingTimeEditText.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
        val ingredients = selectedIngredients.values.map {
            RecipeIngredientRequest(
                ingredientName = it.name,
                quantity = it.quantity.trim().takeIf { value -> value.isNotBlank() },
                unit = it.unit.trim().takeIf { value -> value.isNotBlank() }
            )
        }

        if (title.isBlank() || instructions.isEmpty() || ingredients.isEmpty()) {
            return null
        }

        return RecipeRequest(
            title = title,
            ingredients = ingredients,
            instructions = instructions,
            cookingTimeMinutes = cookingTime
        )
    }

    private fun showFormError(message: String) {
        binding.formErrorText.text = message
        binding.formErrorText.visibility = View.VISIBLE
    }

    private fun setSavingState(isSaving: Boolean) {
        binding.saveButton.isEnabled = !isSaving
        binding.cancelButton.isEnabled = !isSaving
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "recipe_changed"
        const val RESULT_CHANGED = "changed"
        private const val ARG_RECIPE_ID = "recipe_id"

        fun newInstance(recipeId: Int? = null): RecipeFormDialogFragment {
            return RecipeFormDialogFragment().apply {
                arguments = bundleOf(ARG_RECIPE_ID to (recipeId ?: 0))
            }
        }
    }
}
