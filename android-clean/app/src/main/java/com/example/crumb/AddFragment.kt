package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.IngredientRepository
import com.example.crumb.data.IngredientResponse
import com.example.crumb.databinding.FragmentAddBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class AddFragment : Fragment() {
    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!
    private val ingredientRepository = IngredientRepository()
    private val ingredientAdapter = IngredientCategoryAdapter(::selectIngredient)
    private val allIngredients = mutableListOf<IngredientResponse>()
    private val selectedIngredients = linkedMapOf<String, IngredientResponse>()
    private var loadIngredientsJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ingredientRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ingredientRecyclerView.adapter = ingredientAdapter
        binding.ingredientSearchEditText.doAfterTextChanged {
            updateIngredientList()
        }
        binding.retryButton.setOnClickListener {
            loadIngredients()
        }
        binding.createRecipeButton.setOnClickListener {
            RecipeFormDialogFragment.newInstance()
                .show(parentFragmentManager, "RecipeFormDialog")
        }
        binding.createCommunityPostButton.setOnClickListener {
            CommunityPostFormDialogFragment.newInstance()
                .show(parentFragmentManager, "CommunityPostFormDialog")
        }

        loadIngredients()
    }

    private fun loadIngredients() {
        loadIngredientsJob?.cancel()
        showLoading()

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
                    showError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.ingredients_error)
                    )
                }
            )
        }
    }

    private fun updateIngredientList() {
        val query = binding.ingredientSearchEditText.text?.toString().orEmpty()
        ingredientAdapter.submitIngredients(allIngredients, query)

        val hasIngredients = allIngredients.isNotEmpty()
        val hasMatches = ingredientAdapter.itemCount > 0
        binding.loadingText.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.ingredientRecyclerView.visibility = if (hasMatches) View.VISIBLE else View.GONE
        binding.emptyText.visibility = if (!hasIngredients || !hasMatches) View.VISIBLE else View.GONE
        binding.emptyText.text = if (hasIngredients) {
            getString(R.string.ingredients_no_matches)
        } else {
            getString(R.string.ingredients_empty)
        }
    }

    private fun selectIngredient(ingredient: IngredientResponse) {
        val key = ingredient.name.lowercase(Locale.US)
        if (selectedIngredients.containsKey(key)) return

        selectedIngredients[key] = ingredient
        updateSelectedIngredients()
    }

    private fun updateSelectedIngredients() {
        binding.selectedIngredientsChipGroup.removeAllViews()
        selectedIngredients.values.forEach { ingredient ->
            val chip = Chip(requireContext()).apply {
                text = ingredient.name
                isCloseIconVisible = true
                setOnClickListener {
                    removeSelectedIngredient(ingredient)
                }
                setOnCloseIconClickListener {
                    removeSelectedIngredient(ingredient)
                }
            }
            binding.selectedIngredientsChipGroup.addView(chip)
        }

        binding.selectedIngredientsEmpty.visibility =
            if (selectedIngredients.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun removeSelectedIngredient(ingredient: IngredientResponse) {
        selectedIngredients.remove(ingredient.name.lowercase(Locale.US))
        updateSelectedIngredients()
    }

    private fun showLoading() {
        binding.loadingText.visibility = View.VISIBLE
        binding.loadingText.text = getString(R.string.ingredients_loading)
        binding.emptyText.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.retryButton.isEnabled = false
        binding.ingredientRecyclerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingText.visibility = View.GONE
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = message
        binding.retryButton.visibility = View.VISIBLE
        binding.retryButton.isEnabled = true
        binding.ingredientRecyclerView.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
