package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.SavedRecipeAdapter.SavedRecipeListItem
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.data.SavedRecipeRepository
import com.example.crumb.data.SavedRecipeResponse
import com.example.crumb.databinding.FragmentSavedBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SavedFragment : Fragment() {
    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!
    private val savedRecipeRepository = SavedRecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val savedRecipeAdapter = SavedRecipeAdapter(
        onRecipeClick = ::openRecipeDetails,
        onUnsaveClick = ::unsaveRecipe
    )
    private var loadSavedJob: Job? = null
    private var unsaveJob: Job? = null
    private var isNavigatingToRecipePost = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.savedRecipesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.savedRecipesRecyclerView.adapter = savedRecipeAdapter
        binding.savedRetryButton.setOnClickListener { loadSavedRecipes() }
        binding.savedBrowseButton.setOnClickListener { navigateToHome() }
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToRecipePost = false
        loadSavedRecipes()
    }

    private fun loadSavedRecipes() {
        loadSavedJob?.cancel()
        showLoading()

        loadSavedJob = viewLifecycleOwner.lifecycleScope.launch {
            val savedResult = savedRecipeRepository.getSavedRecipes()
            if (_binding == null) return@launch

            savedResult.fold(
                onSuccess = { savedRecipes ->
                    val communityPosts = postRepository.getCommunityPosts().getOrNull().orEmpty()
                    val postIdsByRecipeId = communityPosts.postIdsByRecipeId()
                    val items = savedRecipes.map { savedRecipe ->
                        SavedRecipeListItem(
                            savedRecipe = savedRecipe,
                            postId = postIdsByRecipeId[savedRecipe.recipe.id]
                        )
                    }
                    showContent(items)
                },
                onFailure = { error ->
                    showError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.saved_error)
                    )
                }
            )
        }
    }

    private fun showLoading() {
        binding.savedStatusText.text = getString(R.string.saved_loading)
        binding.savedStatusText.visibility = View.VISIBLE
        binding.savedRetryButton.visibility = View.GONE
        binding.savedRetryButton.isEnabled = false
        binding.savedEmptyContainer.visibility = View.GONE
        binding.savedRecipesRecyclerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.savedStatusText.text = message
        binding.savedStatusText.visibility = View.VISIBLE
        binding.savedRetryButton.visibility = View.VISIBLE
        binding.savedRetryButton.isEnabled = true
        binding.savedEmptyContainer.visibility = View.GONE
        binding.savedRecipesRecyclerView.visibility = View.GONE
    }

    private fun showEmpty() {
        savedRecipeAdapter.submitItems(emptyList())
        binding.savedStatusText.visibility = View.GONE
        binding.savedRetryButton.visibility = View.GONE
        binding.savedRetryButton.isEnabled = true
        binding.savedEmptyContainer.visibility = View.VISIBLE
        binding.savedRecipesRecyclerView.visibility = View.GONE
    }

    private fun showContent(items: List<SavedRecipeListItem>) {
        if (items.isEmpty()) {
            showEmpty()
            return
        }

        savedRecipeAdapter.submitItems(items)
        binding.savedStatusText.visibility = View.GONE
        binding.savedRetryButton.visibility = View.GONE
        binding.savedRetryButton.isEnabled = true
        binding.savedEmptyContainer.visibility = View.GONE
        binding.savedRecipesRecyclerView.visibility = View.VISIBLE
    }

    private fun unsaveRecipe(savedRecipe: SavedRecipeResponse) {
        val recipeId = savedRecipe.recipe.id
        if (unsaveJob?.isActive == true) {
            return
        }

        savedRecipeAdapter.setUnsavePending(recipeId, true)
        unsaveJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = savedRecipeRepository.unsaveRecipe(recipeId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    savedRecipeAdapter.removeRecipe(recipeId)
                    savedRecipeAdapter.setUnsavePending(recipeId, false)
                    if (savedRecipeAdapter.currentItems().isEmpty()) {
                        showEmpty()
                    }
                    Toast.makeText(
                        requireContext(),
                        R.string.recipe_removed_from_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { error ->
                    savedRecipeAdapter.setUnsavePending(recipeId, false)
                    Toast.makeText(
                        requireContext(),
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.generic_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun openRecipeDetails(item: SavedRecipeListItem) {
        if (isNavigatingToRecipePost) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.savedFragment) {
            return
        }

        isNavigatingToRecipePost = true
        val args = Bundle().apply {
            putInt(RecipePostDetailFragment.ARG_RECIPE_ID, item.savedRecipe.recipe.id)
            item.postId?.let { postId ->
                if (postId > 0) {
                    putInt(RecipePostDetailFragment.ARG_POST_ID, postId)
                }
            }
        }

        try {
            navController.navigate(R.id.recipePostDetailFragment, args)
        } catch (_: IllegalArgumentException) {
            isNavigatingToRecipePost = false
        }
    }

    private fun navigateToHome() {
        val navController = findNavController()
        if (!navController.popBackStack(R.id.homeFragment, false)) {
            navController.navigate(R.id.homeFragment)
        }
    }

    private fun List<CommunityPostResponse>.postIdsByRecipeId(): Map<Int, Int> {
        return mapNotNull { post ->
            post.recipeId?.let { recipeId -> recipeId to post.id }
        }.toMap()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadSavedJob?.cancel()
        unsaveJob?.cancel()
        binding.savedRecipesRecyclerView.adapter = null
        _binding = null
    }
}
