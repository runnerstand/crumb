package com.example.crumb

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.IngredientRepository
import com.example.crumb.data.IngredientResponse
import com.example.crumb.data.RecipeIngredientRequest
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeRequest
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentAddBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class EditRecipeFragment : Fragment() {
    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!
    private val ingredientRepository = IngredientRepository()
    private val recipeRepository = RecipeRepository()
    private val ingredientAdapter = IngredientCategoryAdapter(::selectIngredient)
    private val selectedIngredientAdapter = SelectedRecipeIngredientAdapter(
        onRemoveClick = ::removeIngredient,
        onIngredientChanged = ::onSelectedIngredientChanged
    )
    private val allIngredients = mutableListOf<IngredientResponse>()
    private val selectedIngredients = linkedMapOf<String, SelectedRecipeIngredient>()
    private var loadIngredientsJob: Job? = null
    private var loadRecipeJob: Job? = null
    private var saveRecipeJob: Job? = null
    private var originalRequest: RecipeRequest? = null
    private var isPopulating = false
    private var isPreferenceWarningShowing = false
    private var currentImageUrl: String? = null
    private var selectedImageUri: Uri? = null
    private var isImageCleared = false
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && _binding != null) {
            selectedImageUri = uri
            isImageCleared = false
            updateImagePreview()
            onFormChanged()
        }
    }

    private val recipeId: Int
        get() = arguments?.getInt(ARG_RECIPE_ID, NO_RECIPE_ID) ?: NO_RECIPE_ID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.formTitle.text = getString(R.string.edit_recipe)
        binding.draftRestoredText.visibility = View.GONE
        binding.cancelButton.text = getString(R.string.cancel)
        binding.saveButton.text = getString(R.string.save_changes)
        binding.recipeTitleEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_TITLE_LENGTH))
        binding.instructionsEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_INSTRUCTIONS_LENGTH))
        binding.ingredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ingredientRecyclerView.adapter = ingredientAdapter
        binding.selectedIngredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedIngredientRecyclerView.adapter = selectedIngredientAdapter
        binding.recipeTitleEditText.doAfterTextChanged { onFormChanged() }
        binding.cookingTimeEditText.doAfterTextChanged { onFormChanged() }
        binding.servingsEditText.doAfterTextChanged { onFormChanged() }
        binding.instructionsEditText.doAfterTextChanged { onFormChanged() }
        binding.ingredientSearchEditText.doAfterTextChanged { updateIngredientList() }
        binding.ingredientRetryButton.setOnClickListener { loadIngredients() }
        binding.cancelButton.setOnClickListener { confirmExitIfChanged() }
        binding.saveButton.setOnClickListener { saveChanges() }
        binding.chooseImageButton.setOnClickListener { openImagePicker() }
        binding.clearImageButton.setOnClickListener {
            selectedImageUri = null
            isImageCleared = true
            updateImagePreview()
            onFormChanged()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            confirmExitIfChanged()
        }

        if (recipeId <= 0) {
            Toast.makeText(requireContext(), R.string.generic_error, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        loadIngredients()
        loadRecipe()
        updateSelectedIngredients()
    }

    private fun loadIngredients() {
        loadIngredientsJob?.cancel()
        binding.ingredientStatusText.text = getString(R.string.ingredients_loading)
        binding.ingredientStatusText.visibility = View.VISIBLE
        binding.ingredientRetryButton.visibility = View.GONE
        binding.ingredientRetryButton.isEnabled = false
        binding.ingredientRecyclerView.visibility = View.GONE

        loadIngredientsJob = viewLifecycleOwner.lifecycleScope.launch {
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
                    binding.ingredientRetryButton.isEnabled = true
                    binding.ingredientRecyclerView.visibility = View.GONE
                }
            )
        }
    }

    private fun loadRecipe() {
        loadRecipeJob?.cancel()
        setSavingState(true)

        loadRecipeJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getUserRecipe(recipeId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipe ->
                    populateRecipe(recipe)
                    setSavingState(false)
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.generic_error))
                    setSavingState(false)
                }
            )
        }
    }

    private fun populateRecipe(recipe: RecipeResponse) {
        isPopulating = true
        try {
            binding.recipeTitleEditText.setText(recipe.title)
            binding.cookingTimeEditText.setText(recipe.cookingTimeMinutes?.toString().orEmpty())
            binding.servingsEditText.setText(recipe.servings.coerceIn(MIN_SERVINGS, MAX_SERVINGS).toString())
            binding.instructionsEditText.setText(recipe.instructions.joinToString("\n"))
            binding.ingredientSearchEditText.text = null
            selectedIngredients.clear()
            recipe.ingredients.forEach { ingredient ->
                val selected = SelectedRecipeIngredient(
                    name = ingredient.ingredientName,
                    quantity = ingredient.quantity.orEmpty(),
                    unit = ingredient.unit.orEmpty()
                )
                selectedIngredients[selected.name.lowercase(Locale.US)] = selected
            }
            currentImageUrl = recipe.imageUrl
            selectedImageUri = null
            isImageCleared = false
            updateImagePreview()
            updateSelectedIngredients()
            originalRequest = buildRecipeRequest(showErrors = false)
            binding.formErrorText.visibility = View.GONE
        } finally {
            isPopulating = false
        }
    }

    private fun updateIngredientList() {
        val query = binding.ingredientSearchEditText.text?.toString().orEmpty()
        ingredientAdapter.submitIngredients(allIngredients, query)
        val hasMatches = ingredientAdapter.itemCount > 0
        binding.ingredientRecyclerView.visibility = if (hasMatches) View.VISIBLE else View.GONE
        binding.ingredientStatusText.visibility = if (hasMatches) View.GONE else View.VISIBLE
        binding.ingredientStatusText.text = when {
            allIngredients.isEmpty() -> getString(R.string.ingredients_empty)
            else -> getString(R.string.ingredients_no_matches)
        }
        binding.ingredientRetryButton.visibility = View.GONE
    }

    private fun selectIngredient(ingredient: IngredientResponse) {
        val key = ingredient.name.lowercase(Locale.US)
        if (selectedIngredients.containsKey(key)) return

        selectedIngredients[key] = SelectedRecipeIngredient(name = ingredient.name)
        updateSelectedIngredients()
        onFormChanged()
    }

    private fun removeIngredient(ingredient: SelectedRecipeIngredient) {
        selectedIngredients.remove(ingredient.name.lowercase(Locale.US))
        updateSelectedIngredients()
        onFormChanged()
    }

    private fun onSelectedIngredientChanged() {
        onFormChanged()
    }

    private fun onFormChanged() {
        if (isPopulating) {
            return
        }

        if (binding.formErrorText.visibility == View.VISIBLE) {
            binding.formErrorText.visibility = View.GONE
        }
    }

    private fun updateSelectedIngredients() {
        selectedIngredientAdapter.submitIngredients(selectedIngredients.values.toList())
        binding.selectedIngredientRecyclerView.requestLayout()
        binding.selectedEmptyText.visibility =
            if (selectedIngredients.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveChanges(skipPreferenceWarning: Boolean = false) {
        if (!binding.saveButton.isEnabled || saveRecipeJob?.isActive == true) {
            return
        }

        val request = buildRecipeRequest(showErrors = true) ?: return
        if (!skipPreferenceWarning) {
            val conflicts = RecipePreferenceConflictChecker.findConflicts(
                context = requireContext(),
                selectedIngredientNames = selectedIngredients.values.map { it.name }
            )
            if (conflicts.isNotEmpty()) {
                showPreferenceWarning(conflicts)
                return
            }
        }

        setSavingState(true)
        saveRecipeJob = viewLifecycleOwner.lifecycleScope.launch {
            val imageUploadResult = uploadSelectedImageUrl()
            if (imageUploadResult.isFailure) return@launch
            val finalRequest = request.copy(imageUrl = imageUploadResult.getOrNull())
            val result = recipeRepository.updateUserRecipe(recipeId, finalRequest)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    currentImageUrl = finalRequest.imageUrl
                    selectedImageUri = null
                    isImageCleared = false
                    originalRequest = finalRequest
                    setFragmentResult(
                        RecipeFormDialogFragment.REQUEST_KEY,
                        bundleOf(RecipeFormDialogFragment.RESULT_CHANGED to true)
                    )
                    Toast.makeText(requireContext(), R.string.recipe_changes_saved, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.generic_error))
                    setSavingState(false)
                }
            )
        }
    }

    private suspend fun uploadSelectedImageUrl(): Result<String?> {
        val imageUri = selectedImageUri
        if (imageUri == null) {
            return Result.success(if (isImageCleared) null else currentImageUrl)
        }

        val result = recipeRepository.uploadRecipeImage(requireContext(), imageUri)
        if (_binding == null) return Result.failure(IllegalStateException("View is no longer available."))

        return result.fold(
            onSuccess = { upload -> Result.success(upload.imageUrl) },
            onFailure = { error ->
                showFormError(error.message ?: getString(R.string.generic_error))
                setSavingState(false)
                Result.failure(error)
            }
        )
    }

    private fun openImagePicker() {
        if (!binding.chooseImageButton.isEnabled) {
            return
        }

        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun updateImagePreview() {
        val imageUri = selectedImageUri
        when {
            imageUri != null -> {
                binding.recipeImagePreview.setImageURI(imageUri)
                binding.chooseImageButton.text = getString(R.string.replace_recipe_image)
                binding.clearImageButton.visibility = View.VISIBLE
            }
            !isImageCleared && !currentImageUrl.isNullOrBlank() -> {
                binding.recipeImagePreview.loadRecipeImage(currentImageUrl)
                binding.chooseImageButton.text = getString(R.string.replace_recipe_image)
                binding.clearImageButton.visibility = View.VISIBLE
            }
            else -> {
                binding.recipeImagePreview.setImageResource(R.drawable.recipe_placeholder)
                binding.chooseImageButton.text = getString(R.string.choose_recipe_image)
                binding.clearImageButton.visibility = View.GONE
            }
        }
    }

    private fun showPreferenceWarning(conflicts: List<RecipePreferenceConflict>) {
        if (isPreferenceWarningShowing || _binding == null) {
            return
        }

        isPreferenceWarningShowing = true
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.recipe_preference_warning_title)
            .setMessage(
                getString(
                    R.string.recipe_preference_warning_message,
                    conflicts.joinToString(separator = "\n") { conflict ->
                        "- ${conflict.ingredientName} (${conflict.reason.displayName()})"
                    }
                )
            )
            .setNegativeButton(R.string.recipe_preference_warning_go_back, null)
            .setPositiveButton(R.string.recipe_preference_warning_save_anyway) { _, _ ->
                saveChanges(skipPreferenceWarning = true)
            }
            .setOnDismissListener {
                isPreferenceWarningShowing = false
            }
            .show()
    }

    private fun buildRecipeRequest(showErrors: Boolean): RecipeRequest? {
        val title = binding.recipeTitleEditText.text?.toString()?.trim().orEmpty()
        val instructions = binding.instructionsEditText.text?.toString()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val cookingTime = binding.cookingTimeEditText.text?.toString()?.trim().orEmpty()
        val parsedCookingTime = cookingTime.takeIf { it.isNotBlank() }?.toIntOrNull()
        val servingsText = binding.servingsEditText.text?.toString()?.trim().orEmpty()
        val parsedServings = servingsText.toIntOrNull()
        val ingredients = selectedIngredients.values.map {
            RecipeIngredientRequest(
                ingredientName = it.name,
                quantity = it.quantity.trim().takeIf { value -> value.isNotBlank() },
                unit = it.unit.trim().takeIf { value -> value.isNotBlank() }
            )
        }

        fun fail(messageRes: Int): RecipeRequest? {
            if (showErrors) {
                showFormError(getString(messageRes))
            }
            return null
        }

        if (title.isBlank()) return fail(R.string.recipe_title_required)
        if (title.length > MAX_TITLE_LENGTH) return fail(R.string.recipe_title_too_long)
        if (cookingTime.isNotBlank() && (parsedCookingTime == null || parsedCookingTime <= 0)) {
            return fail(R.string.recipe_cooking_time_invalid)
        }
        if (parsedServings == null || parsedServings !in MIN_SERVINGS..MAX_SERVINGS) {
            return fail(R.string.recipe_servings_invalid)
        }
        if (instructions.isEmpty()) return fail(R.string.recipe_instructions_required)
        if (instructions.any { it.length > MAX_INSTRUCTION_LINE_LENGTH }) {
            return fail(R.string.recipe_instructions_too_long)
        }
        if (ingredients.isEmpty()) return fail(R.string.recipe_ingredients_required)
        selectedIngredients.values.forEach { ingredient ->
            val quantity = ingredient.quantity.trim()
            val unit = ingredient.unit.trim()
            if (quantity.isBlank()) return fail(R.string.recipe_quantity_required)
            if (!quantity.isPositiveQuantity()) return fail(R.string.recipe_quantity_invalid)
            if (unit.isBlank()) return fail(R.string.recipe_unit_required)
        }

        return RecipeRequest(
            title = title,
            ingredients = ingredients,
            instructions = instructions,
            cookingTimeMinutes = parsedCookingTime,
            servings = parsedServings,
            imageUrl = if (isImageCleared) null else currentImageUrl
        )
    }

    private fun hasMeaningfulChanges(): Boolean {
        if (selectedImageUri != null || isImageCleared) {
            return true
        }

        val original = originalRequest ?: return false
        val current = buildRecipeRequest(showErrors = false) ?: return true
        return current != original
    }

    private fun confirmExitIfChanged() {
        if (!hasMeaningfulChanges()) {
            findNavController().navigateUp()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discard_changes_title)
            .setMessage(R.string.discard_changes_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.discard_changes) { _, _ ->
                findNavController().navigateUp()
            }
            .show()
    }

    private fun showFormError(message: String) {
        binding.formErrorText.text = message
        binding.formErrorText.visibility = View.VISIBLE
    }

    private fun setSavingState(isSaving: Boolean) {
        binding.saveButton.isEnabled = !isSaving
        binding.cancelButton.isEnabled = !isSaving
        binding.chooseImageButton.isEnabled = !isSaving
        binding.clearImageButton.isEnabled = !isSaving
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadIngredientsJob?.cancel()
        loadRecipeJob?.cancel()
        saveRecipeJob?.cancel()
        binding.ingredientRecyclerView.adapter = null
        binding.selectedIngredientRecyclerView.adapter = null
        _binding = null
    }

    companion object {
        const val ARG_RECIPE_ID = "recipeId"
        private const val NO_RECIPE_ID = -1
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_INSTRUCTIONS_LENGTH = 4000
        private const val MAX_INSTRUCTION_LINE_LENGTH = 1000
        private const val MIN_SERVINGS = 1
        private const val MAX_SERVINGS = 20
    }
}

private fun String.isPositiveQuantity(): Boolean {
    val decimalValue = toDoubleOrNull()
    if (decimalValue != null) {
        return decimalValue > 0
    }

    val parts = split("/")
    if (parts.size != 2) {
        return false
    }

    val numerator = parts[0].trim().toDoubleOrNull() ?: return false
    val denominator = parts[1].trim().toDoubleOrNull() ?: return false
    return numerator > 0 && denominator > 0
}

private fun ConflictReason.displayName(): String {
    return when (this) {
        ConflictReason.VEGETARIAN -> "Vegetarian"
        ConflictReason.AVOID -> "Avoided ingredient"
    }
}
