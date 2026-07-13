package com.example.crumb.feature.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.databinding.FragmentSearchBinding
import com.example.crumb.ui.adapter.RecipeAdapter
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.example.crumb.ui.screens.search.RecipeSearchFilters
import com.example.crumb.ui.screens.search.SearchUiState
import com.example.crumb.ui.screens.search.SearchViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var recipeAdapter: RecipeAdapter
    private var selectedCategory: String? = null
    private var categories: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recipeAdapter = RecipeAdapter(
            onViewRecipe = ::showRecipeDetails,
            onSaveRecipe = viewModel::saveRecipe,
            onUnsaveRecipe = viewModel::unsaveRecipe,
            onEditRecipe = {},
            onDeleteRecipe = {},
            showOwnerActions = false
        )
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.searchResultsRecyclerView.adapter = recipeAdapter
        binding.searchRetryButton.setOnClickListener { viewModel.loadSearchData() }
        binding.clearFiltersButton.setOnClickListener { clearFilters() }
        binding.savedOnlyCheckBox.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.searchEditText.addTextChangedListener(simpleTextWatcher { applyFilters() })
        binding.cookingTimeEditText.addTextChangedListener(simpleTextWatcher { applyFilters() })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderSearchState) }
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
        viewModel.loadSearchData()
    }

    private fun renderSearchState(state: SearchUiState) = with(binding) {
        searchProgressBar.visibility = if (state is SearchUiState.Loading) View.VISIBLE else View.GONE
        searchResultsRecyclerView.visibility =
            if (state is SearchUiState.Success) View.VISIBLE else View.GONE
        searchRetryButton.visibility = if (state is SearchUiState.Error) View.VISIBLE else View.GONE
        searchStatusTextView.visibility =
            if (state is SearchUiState.Error || state is SearchUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (state) {
            SearchUiState.Loading -> searchStatusTextView.text = ""
            is SearchUiState.Empty -> {
                searchStatusTextView.text = "No recipes match these filters."
                updateCategoryDropdown(state.categories)
                recipeAdapter.submitList(emptyList())
            }
            is SearchUiState.Error -> {
                searchStatusTextView.text = state.message
                recipeAdapter.submitList(emptyList())
            }
            is SearchUiState.Success -> {
                updateCategoryDropdown(state.categories)
                recipeAdapter.submitList(state.recipes)
            }
        }
    }

    private fun updateCategoryDropdown(nextCategories: List<String>) = with(binding) {
        if (categories == nextCategories) {
            return
        }

        categories = nextCategories
        val labels = listOf("All categories") + categories
        categoryAutoCompleteTextView.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )
        categoryAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = if (position == 0) null else categories[position - 1]
            applyFilters()
        }
        if (categoryAutoCompleteTextView.text.isNullOrBlank() ||
            selectedCategory !in categories
        ) {
            selectedCategory = null
            categoryAutoCompleteTextView.setText("All categories", false)
        }
    }

    private fun applyFilters() = with(binding) {
        viewModel.updateFilters(
            RecipeSearchFilters(
                keyword = searchEditText.text?.toString().orEmpty(),
                category = selectedCategory,
                maxCookingTimeMinutes = cookingTimeEditText.text?.toString()
                    ?.trim()
                    ?.ifBlank { null }
                    ?.toIntOrNull(),
                savedOnly = savedOnlyCheckBox.isChecked
            )
        )
    }

    private fun clearFilters() = with(binding) {
        selectedCategory = null
        searchEditText.setText("")
        cookingTimeEditText.setText("")
        savedOnlyCheckBox.isChecked = false
        categoryAutoCompleteTextView.setText("All categories", false)
        viewModel.clearFilters()
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

    override fun onDestroyView() {
        binding.searchResultsRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private fun simpleTextWatcher(onTextChanged: () -> Unit): TextWatcher {
    return object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged()
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
}
