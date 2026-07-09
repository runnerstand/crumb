package com.example.crumb.feature.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.R
import com.example.crumb.databinding.FragmentCreatePostBinding
import com.example.crumb.ui.adapter.IngredientAdapter
import com.example.crumb.ui.adapter.toDisplayLabel
import com.example.crumb.ui.screens.create.CreatePostUiState
import com.example.crumb.ui.screens.create.CreatePostViewModel
import com.example.crumb.ui.screens.create.IngredientCatalogueUiState
import com.example.crumb.ui.screens.create.IngredientPickerItem
import com.example.crumb.ui.screens.create.PostRecipePickerItem
import com.example.crumb.ui.screens.create.PostRecipePickerUiState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CreatePostFragment : Fragment() {
    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: CreatePostViewModel by viewModels()
    private lateinit var ingredientAdapter: IngredientAdapter
    private var allIngredients: List<IngredientPickerItem> = emptyList()
    private var selectedIngredients: List<String> = emptyList()
    private var recipeOptions: List<PostRecipePickerItem> = emptyList()
    private var selectedCategory: String? = null
    private var query: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ingredientAdapter = IngredientAdapter { ingredient ->
            viewModel.addIngredient(ingredient.name)
            binding.searchEditText.setText("")
        }
        binding.ingredientsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ingredientsRecyclerView.adapter = ingredientAdapter

        binding.publishButton.setOnClickListener {
            viewModel.createPost(
                title = binding.titleEditText.text.toString(),
                caption = binding.captionEditText.text.toString()
            )
        }
        binding.searchEditText.addTextChangedListener(simpleTextWatcher {
            query = it.trim()
            renderFilteredIngredients()
        })
        binding.captionEditText.addTextChangedListener(simpleTextWatcher {
            binding.captionCountTextView.text = "${it.length}/200"
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderFormState) }
                launch { viewModel.catalogueState.collect(::renderCatalogueState) }
                launch { viewModel.recipePickerState.collect(::renderRecipePickerState) }
                launch {
                    viewModel.selectedIngredients.collect {
                        selectedIngredients = it
                        renderSelectedIngredients()
                        renderFilteredIngredients()
                    }
                }
            }
        }
    }

    private fun renderFormState(state: CreatePostUiState) = with(binding) {
        publishButton.isEnabled =
            state !is CreatePostUiState.Saving &&
                viewModel.catalogueState.value is IngredientCatalogueUiState.Success
        publishButton.text = if (state is CreatePostUiState.Saving) "Publishing..." else "Publish Post"
        formMessageTextView.visibility = if (state is CreatePostUiState.Error) View.VISIBLE else View.GONE
        formMessageTextView.text = if (state is CreatePostUiState.Error) state.message else ""

        if (state is CreatePostUiState.Success) {
            titleEditText.setText("")
            captionEditText.setText("")
            searchEditText.setText("")
            recipeAutoCompleteTextView.setText("No linked recipe", false)
            Snackbar.make(root, "Post created.", Snackbar.LENGTH_SHORT).show()
            viewModel.resetState()
            findNavController().navigate(R.id.homeFragment)
        }
    }

    private fun renderRecipePickerState(state: PostRecipePickerUiState) = with(binding) {
        recipeProgressBar.visibility =
            if (state is PostRecipePickerUiState.Loading) View.VISIBLE else View.GONE
        recipeStatusTextView.visibility =
            if (state is PostRecipePickerUiState.Error || state is PostRecipePickerUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }
        recipeInputLayout.isEnabled = state is PostRecipePickerUiState.Success

        when (state) {
            PostRecipePickerUiState.Loading -> {
                recipeStatusTextView.text = ""
                recipeOptions = emptyList()
                setRecipeDropdown(emptyList())
            }
            PostRecipePickerUiState.Empty -> {
                recipeStatusTextView.text = "No user-created recipes available to link."
                recipeOptions = emptyList()
                viewModel.selectRecipe(null)
                setRecipeDropdown(emptyList())
            }
            is PostRecipePickerUiState.Error -> {
                recipeStatusTextView.text = state.message
                recipeOptions = emptyList()
                viewModel.selectRecipe(null)
                setRecipeDropdown(emptyList())
            }
            is PostRecipePickerUiState.Success -> {
                recipeOptions = state.recipes
                recipeStatusTextView.text = ""
                setRecipeDropdown(state.recipes)
            }
        }
    }

    private fun setRecipeDropdown(recipes: List<PostRecipePickerItem>) = with(binding) {
        val labels = listOf("No linked recipe") + recipes.map { it.displayLabel }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )
        recipeAutoCompleteTextView.setAdapter(adapter)
        if (recipeAutoCompleteTextView.text.isNullOrBlank() ||
            recipeOptions.none { it.id == viewModel.selectedRecipeId.value }
        ) {
            recipeAutoCompleteTextView.setText("No linked recipe", false)
        }
        recipeAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectRecipe(if (position == 0) null else recipes[position - 1].id)
        }
    }

    private fun renderCatalogueState(state: IngredientCatalogueUiState) = with(binding) {
        catalogueProgressBar.visibility =
            if (state is IngredientCatalogueUiState.Loading) View.VISIBLE else View.GONE
        catalogueStatusTextView.visibility =
            if (state is IngredientCatalogueUiState.Error || state is IngredientCatalogueUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }
        ingredientsRecyclerView.visibility =
            if (state is IngredientCatalogueUiState.Success) View.VISIBLE else View.GONE
        publishButton.isEnabled =
            state is IngredientCatalogueUiState.Success && viewModel.uiState.value !is CreatePostUiState.Saving

        when (state) {
            IngredientCatalogueUiState.Loading -> {
                catalogueStatusTextView.text = ""
                allIngredients = emptyList()
            }
            IngredientCatalogueUiState.Empty -> {
                catalogueStatusTextView.text = "No supported ingredients are available from the backend."
                allIngredients = emptyList()
            }
            is IngredientCatalogueUiState.Error -> {
                catalogueStatusTextView.text = state.message
                allIngredients = emptyList()
            }
            is IngredientCatalogueUiState.Success -> {
                allIngredients = state.ingredients
                renderCategoryChips()
                renderFilteredIngredients()
            }
        }
    }

    private fun renderSelectedIngredients() = with(binding.selectedChipGroup) {
        removeAllViews()
        if (selectedIngredients.isEmpty()) {
            addView(Chip(requireContext()).apply {
                text = "Select at least one ingredient"
                isEnabled = false
            })
            return
        }
        selectedIngredients.forEach { ingredient ->
            addView(Chip(requireContext()).apply {
                text = ingredient
                isCloseIconVisible = true
                setOnCloseIconClickListener { viewModel.removeIngredient(ingredient) }
            })
        }
    }

    private fun renderCategoryChips() = with(binding.categoryChipGroup) {
        removeAllViews()
        addView(Chip(requireContext()).apply {
            id = View.generateViewId()
            text = "All"
            isCheckable = true
            isChecked = selectedCategory == null
            setOnClickListener {
                selectedCategory = null
                clearCategoryChecksExcept(id)
                renderFilteredIngredients()
            }
        })
        allIngredients.map { it.category }.distinct().sorted().forEach { category ->
            addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category.toDisplayLabel()
                tag = category
                isCheckable = true
                isChecked = selectedCategory == category
                setOnClickListener {
                    selectedCategory = category
                    clearCategoryChecksExcept(id)
                    renderFilteredIngredients()
                }
            })
        }
    }

    private fun clearCategoryChecksExcept(checkedId: Int) {
        binding.categoryChipGroup.children
            .filterIsInstance<Chip>()
            .forEach { it.isChecked = it.id == checkedId }
    }

    private fun renderFilteredIngredients() {
        val normalizedQuery = query.lowercase()
        val filtered = allIngredients
            .filterNot { selectedIngredients.contains(it.name) }
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter {
                normalizedQuery.isBlank() ||
                    it.name.contains(normalizedQuery, ignoreCase = true) ||
                    it.category.contains(normalizedQuery, ignoreCase = true)
            }
        ingredientAdapter.submitList(filtered)
        binding.catalogueStatusTextView.visibility =
            if (allIngredients.isNotEmpty() && filtered.isEmpty()) View.VISIBLE else binding.catalogueStatusTextView.visibility
        if (allIngredients.isNotEmpty() && filtered.isEmpty()) {
            binding.catalogueStatusTextView.text = "No matching supported ingredients."
        } else if (allIngredients.isNotEmpty()) {
            binding.catalogueStatusTextView.text = ""
            binding.catalogueStatusTextView.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        binding.ingredientsRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private fun simpleTextWatcher(onTextChanged: (String) -> Unit): TextWatcher {
    return object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
}
