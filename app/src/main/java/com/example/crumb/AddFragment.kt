package com.example.crumb

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostRequest
import com.example.crumb.data.IngredientRepository
import com.example.crumb.data.IngredientResponse
import com.example.crumb.data.RecipeIngredientRequest
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeRequest
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentAddBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AddFragment : Fragment() {
    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!
    private val ingredientRepository = IngredientRepository()
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val ingredientAdapter = IngredientCategoryAdapter(::selectIngredient)
    private val selectedIngredientAdapter = SelectedRecipeIngredientAdapter(
        onRemoveClick = ::removeIngredient,
        onIngredientChanged = ::onSelectedIngredientChanged
    )
    private val draftStore by lazy { RecipeDraftStore(requireContext()) }
    private val allIngredients = mutableListOf<IngredientResponse>()
    private val selectedIngredients = linkedMapOf<String, SelectedRecipeIngredient>()
    private var loadIngredientsJob: Job? = null
    private var saveRecipeJob: Job? = null
    private var draftSaveJob: Job? = null
    private var recipePendingPublish: RecipeResponse? = null
    private var isRestoringDraft = false
    private var isPreferenceWarningShowing = false
    private var selectedImageUri: Uri? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && _binding != null) {
            selectedImageUri = uri
            updateImagePreview()
        }
    }

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

        binding.recipeTitleEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_TITLE_LENGTH))
        binding.instructionsEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_INSTRUCTIONS_LENGTH))
        binding.ingredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ingredientRecyclerView.adapter = ingredientAdapter
        binding.selectedIngredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedIngredientRecyclerView.adapter = selectedIngredientAdapter
        binding.recipeTitleEditText.doAfterTextChanged { onTextFieldChanged() }
        binding.cookingTimeEditText.doAfterTextChanged { onTextFieldChanged() }
        binding.servingsEditText.doAfterTextChanged { onTextFieldChanged() }
        binding.instructionsEditText.doAfterTextChanged { onTextFieldChanged() }
        binding.ingredientSearchEditText.doAfterTextChanged {
            updateIngredientList()
        }
        binding.ingredientRetryButton.setOnClickListener {
            loadIngredients()
        }
        binding.cancelButton.setOnClickListener {
            confirmDiscardDraft()
        }
        binding.saveButton.setOnClickListener {
            saveRecipe()
        }
        binding.chooseImageButton.setOnClickListener {
            openImagePicker()
        }
        binding.clearImageButton.setOnClickListener {
            selectedImageUri = null
            updateImagePreview()
        }

        loadIngredients()
        restoreDraftIfNeeded()
        updateSelectedIngredients()
        updateImagePreview()
    }

    override fun onPause() {
        super.onPause()
        flushDraftSave()
    }

    private fun restoreDraftIfNeeded() {
        val draft = draftStore.load() ?: return
        isRestoringDraft = true
        try {
            binding.recipeTitleEditText.setText(draft.title)
            binding.cookingTimeEditText.setText(draft.cookingTime)
            binding.servingsEditText.setText(draft.servings.ifBlank { DEFAULT_SERVINGS_TEXT })
            binding.instructionsEditText.setText(draft.instructions)
            binding.ingredientSearchEditText.setText("")
            selectedIngredients.clear()
            draft.ingredients.forEach { ingredient ->
                selectedIngredients[ingredient.name.lowercase(Locale.US)] = SelectedRecipeIngredient(
                    name = ingredient.name,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit
                )
            }
            updateSelectedIngredients()
            binding.draftRestoredText.visibility = View.VISIBLE
        } finally {
            isRestoringDraft = false
        }
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
        hideDraftRestoredMessage()
        scheduleDraftSave(immediate = true)
    }

    private fun removeIngredient(ingredient: SelectedRecipeIngredient) {
        selectedIngredients.remove(ingredient.name.lowercase(Locale.US))
        updateSelectedIngredients()
        hideDraftRestoredMessage()
        scheduleDraftSave(immediate = true)
    }

    private fun onSelectedIngredientChanged() {
        if (!canAutosaveDraft()) {
            return
        }

        hideDraftRestoredMessage()
        scheduleDraftSave()
    }

    private fun updateSelectedIngredients() {
        selectedIngredientAdapter.submitIngredients(selectedIngredients.values.toList())
        binding.selectedIngredientRecyclerView.requestLayout()
        binding.selectedEmptyText.visibility =
            if (selectedIngredients.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onTextFieldChanged() {
        if (!canAutosaveDraft()) {
            return
        }

        hideDraftRestoredMessage()
        scheduleDraftSave()
    }

    private fun scheduleDraftSave(immediate: Boolean = false) {
        if (!canAutosaveDraft()) {
            return
        }

        draftSaveJob?.cancel()
        if (immediate) {
            saveDraftNow()
            return
        }

        draftSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            saveDraftNow()
        }
    }

    private fun flushDraftSave() {
        if (!canAutosaveDraft()) {
            return
        }

        draftSaveJob?.cancel()
        saveDraftNow()
    }

    private fun saveDraftNow() {
        if (!canAutosaveDraft()) {
            return
        }

        if (hasMeaningfulInput()) {
            draftStore.save(currentDraft())
        } else {
            draftStore.clear()
        }
    }

    private fun currentDraft(): RecipeDraft {
        return RecipeDraft(
            title = binding.recipeTitleEditText.text?.toString().orEmpty(),
            cookingTime = binding.cookingTimeEditText.text?.toString().orEmpty(),
            servings = binding.servingsEditText.text?.toString().orEmpty(),
            instructions = binding.instructionsEditText.text?.toString().orEmpty(),
            ingredients = selectedIngredients.values.map {
                RecipeDraftIngredient(
                    name = it.name,
                    quantity = it.quantity,
                    unit = it.unit
                )
            }
        )
    }

    private fun canAutosaveDraft(): Boolean {
        return _binding != null && !isRestoringDraft && recipePendingPublish == null
    }

    private fun saveRecipe(skipPreferenceWarning: Boolean = false) {
        if (!binding.saveButton.isEnabled || saveRecipeJob?.isActive == true) {
            return
        }

        binding.formErrorText.visibility = View.GONE
        recipePendingPublish?.let { recipe ->
            publishRecipe(recipe)
            return
        }

        val request = buildRecipeRequest() ?: return
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
            val imageUrl = imageUploadResult.getOrNull()
            val result = recipeRepository.createUserRecipe(request.copy(imageUrl = imageUrl))
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipe ->
                    draftStore.clear()
                    publishRecipe(recipe)
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.generic_error))
                    setSavingState(false)
                }
            )
        }
    }

    private suspend fun uploadSelectedImageUrl(): Result<String?> {
        val imageUri = selectedImageUri ?: return Result.success(null)
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
        if (imageUri == null) {
            binding.recipeImagePreview.setImageResource(R.drawable.recipe_placeholder)
            binding.chooseImageButton.text = getString(R.string.choose_recipe_image)
            binding.clearImageButton.visibility = View.GONE
        } else {
            binding.recipeImagePreview.setImageURI(imageUri)
            binding.chooseImageButton.text = getString(R.string.replace_recipe_image)
            binding.clearImageButton.visibility = View.VISIBLE
        }
    }

    private fun publishRecipe(recipe: RecipeResponse) {
        draftStore.clear()
        setSavingState(true)
        saveRecipeJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = postRepository.createCommunityPost(
                CommunityPostRequest(
                    caption = "",
                    recipeId = recipe.id
                )
            )
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    recipePendingPublish = null
                    finishCreation()
                },
                onFailure = {
                    recipePendingPublish = recipe
                    showFormError(getString(R.string.recipe_saved_publish_failed))
                    binding.saveButton.text = getString(R.string.recipe_publish_retry)
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
            .orEmpty()
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

        if (title.isBlank()) {
            showFormError(getString(R.string.recipe_title_required))
            return null
        }
        if (title.length > MAX_TITLE_LENGTH) {
            showFormError(getString(R.string.recipe_title_too_long))
            return null
        }
        if (cookingTime.isNotBlank() && (parsedCookingTime == null || parsedCookingTime <= 0)) {
            showFormError(getString(R.string.recipe_cooking_time_invalid))
            return null
        }
        if (parsedServings == null || parsedServings !in MIN_SERVINGS..MAX_SERVINGS) {
            showFormError(getString(R.string.recipe_servings_invalid))
            return null
        }
        if (instructions.isEmpty()) {
            showFormError(getString(R.string.recipe_instructions_required))
            return null
        }
        if (instructions.any { it.length > MAX_INSTRUCTION_LINE_LENGTH }) {
            showFormError(getString(R.string.recipe_instructions_too_long))
            return null
        }
        if (ingredients.isEmpty()) {
            showFormError(getString(R.string.recipe_ingredients_required))
            return null
        }
        selectedIngredients.values.forEach { ingredient ->
            val quantity = ingredient.quantity.trim()
            val unit = ingredient.unit.trim()
            if (quantity.isBlank()) {
                showFormError(getString(R.string.recipe_quantity_required))
                return null
            }
            if (!quantity.isPositiveQuantity()) {
                showFormError(getString(R.string.recipe_quantity_invalid))
                return null
            }
            if (unit.isBlank()) {
                showFormError(getString(R.string.recipe_unit_required))
                return null
            }
        }

        return RecipeRequest(
            title = title,
            ingredients = ingredients,
            instructions = instructions,
            cookingTimeMinutes = parsedCookingTime,
            servings = parsedServings
        )
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
                saveRecipe(skipPreferenceWarning = true)
            }
            .setOnDismissListener {
                isPreferenceWarningShowing = false
            }
            .show()
    }

    private fun finishCreation() {
        setFragmentResult(
            RecipeFormDialogFragment.POST_REQUEST_KEY,
            bundleOf(RecipeFormDialogFragment.RESULT_CHANGED to true)
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.recipe_saved),
            Toast.LENGTH_SHORT
        ).show()
        clearVisibleForm(resetDraftMessage = false)
        findNavController().navigate(R.id.homeFragment)
    }

    private fun confirmDiscardDraft() {
        if (!hasMeaningfulInput()) {
            discardDraft()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discard_draft_title)
            .setMessage(R.string.discard_draft_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.discard_draft) { _, _ ->
                discardDraft()
            }
            .show()
    }

    private fun discardDraft() {
        draftStore.clear()
        clearVisibleForm(resetDraftMessage = true)
    }

    private fun clearVisibleForm(resetDraftMessage: Boolean) {
        isRestoringDraft = true
        try {
            recipePendingPublish = null
        binding.recipeTitleEditText.text = null
        binding.cookingTimeEditText.text = null
        binding.servingsEditText.setText(DEFAULT_SERVINGS_TEXT)
        binding.instructionsEditText.text = null
        binding.ingredientSearchEditText.text = null
        selectedIngredients.clear()
        updateSelectedIngredients()
        binding.formErrorText.visibility = View.GONE
        binding.saveButton.text = getString(R.string.save)
        selectedImageUri = null
        updateImagePreview()
        setSavingState(false)
            if (resetDraftMessage) {
                binding.draftRestoredText.visibility = View.GONE
            }
        } finally {
            isRestoringDraft = false
        }
    }

    private fun hasMeaningfulInput(): Boolean {
        return binding.recipeTitleEditText.text?.isNotBlank() == true ||
            binding.cookingTimeEditText.text?.isNotBlank() == true ||
            binding.instructionsEditText.text?.isNotBlank() == true ||
            selectedIngredients.isNotEmpty()
    }

    private fun showFormError(message: String) {
        binding.formErrorText.text = message
        binding.formErrorText.visibility = View.VISIBLE
    }

    private fun hideDraftRestoredMessage() {
        if (_binding == null) {
            return
        }

        if (binding.draftRestoredText.visibility == View.VISIBLE) {
            binding.draftRestoredText.visibility = View.GONE
        }
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
        saveRecipeJob?.cancel()
        draftSaveJob?.cancel()
        if (_binding != null && recipePendingPublish == null && !isRestoringDraft) {
            flushDraftSave()
        }
        binding.ingredientRecyclerView.adapter = null
        binding.selectedIngredientRecyclerView.adapter = null
        _binding = null
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

private const val MAX_TITLE_LENGTH = 120
private const val MAX_INSTRUCTIONS_LENGTH = 4000
private const val MAX_INSTRUCTION_LINE_LENGTH = 1000
private const val DRAFT_SAVE_DEBOUNCE_MS = 750L
private const val MIN_SERVINGS = 1
private const val MAX_SERVINGS = 20
private const val DEFAULT_SERVINGS_TEXT = "2"

private fun ConflictReason.displayName(): String {
    return when (this) {
        ConflictReason.VEGETARIAN -> "Vegetarian"
        ConflictReason.AVOID -> "Avoided ingredient"
    }
}
