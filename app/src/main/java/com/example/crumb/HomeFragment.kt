package com.example.crumb

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentHomeBinding
import com.example.crumb.databinding.ItemCommunityPostBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val postAdapter = CommunityPostAdapter(
        onRecipePostClick = ::openRecipeDetails,
        onLegacyCommentsClick = ::showComments,
        onLegacyEditClick = ::editPost,
        onLegacyDeleteClick = ::confirmDeletePost
    )
    private var loadRecipesJob: Job? = null
    private var loadPostsJob: Job? = null
    private var deletePostJob: Job? = null
    private var isNavigatingToRecipePost = false
    private var isNavigatingToSearch = false
    private var cachedRecipes: List<RecipeResponse> = emptyList()
    private var cachedPosts: List<CommunityPostResponse> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.communityPostsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.communityPostsRecyclerView.adapter = postAdapter
        binding.homeSearchField.setOnClickListener { openSearch() }
        binding.homeRetryButton.setOnClickListener { loadRecipes() }
        binding.communityRetryButton.setOnClickListener { loadCommunityPosts() }
        setFragmentResultListener(CommunityPostFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadCommunityPosts()
        }
        setFragmentResultListener(RecipeFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadRecipes()
            loadCommunityPosts()
        }
        setFragmentResultListener(RecipeFormDialogFragment.POST_REQUEST_KEY) { _, _ ->
            loadRecipes()
            loadCommunityPosts()
        }
        loadRecipes()
        loadCommunityPosts()
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToRecipePost = false
        isNavigatingToSearch = false
    }

    private fun loadRecipes() {
        loadRecipesJob?.cancel()
        showLoading()

        loadRecipesJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getUserRecipes()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipes ->
                    cachedRecipes = recipes
                    renderRecipeSections()
                },
                onFailure = { error ->
                    showError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.recipes_error)
                    )
                }
            )
        }
    }

    private fun loadCommunityPosts() {
        loadPostsJob?.cancel()
        showCommunityLoading()

        loadPostsJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = postRepository.getCommunityPosts()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { posts ->
                    cachedPosts = posts
                    postAdapter.submitPosts(posts)
                    renderRecipeSections()
                    showCommunitySection()
                },
                onFailure = { error ->
                    showCommunityError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.community_posts_error)
                    )
                }
            )
        }
    }

    private fun showLoading() {
        binding.homeStatusText.visibility = View.VISIBLE
        binding.homeStatusText.text = getString(R.string.recipes_loading)
        binding.homeRetryButton.visibility = View.GONE
        binding.homeRetryButton.isEnabled = false
        binding.featuredRecipeCard.visibility = View.GONE
        binding.recentRecipesTitle.visibility = View.GONE
        binding.recentRecipesScroll.visibility = View.GONE
        binding.homeNoRecentRecipes.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.homeStatusText.visibility = View.VISIBLE
        binding.homeStatusText.text = getString(R.string.recipes_empty)
        binding.homeRetryButton.visibility = View.GONE
        binding.homeRetryButton.isEnabled = true
        binding.featuredRecipeCard.visibility = View.GONE
        binding.recentRecipesTitle.visibility = View.GONE
        binding.recentRecipesScroll.visibility = View.GONE
        binding.homeNoRecentRecipes.visibility = View.GONE
        binding.recentRecipesContainer.removeAllViews()
    }

    private fun showError(message: String) {
        binding.homeStatusText.visibility = View.VISIBLE
        binding.homeStatusText.text = message
        binding.homeRetryButton.visibility = View.VISIBLE
        binding.homeRetryButton.isEnabled = true
        binding.featuredRecipeCard.visibility = View.GONE
        binding.recentRecipesTitle.visibility = View.GONE
        binding.recentRecipesScroll.visibility = View.GONE
        binding.homeNoRecentRecipes.visibility = View.GONE
        binding.recentRecipesContainer.removeAllViews()
    }

    private fun showCommunityLoading() {
        binding.communityStatusText.visibility = View.VISIBLE
        binding.communityStatusText.text = getString(R.string.community_posts_loading)
        binding.communityRetryButton.visibility = View.GONE
        binding.communityRetryButton.isEnabled = false
        binding.communityPostsRecyclerView.visibility = View.GONE
    }

    private fun showCommunitySection() {
        binding.communityStatusText.visibility = View.GONE
        binding.communityRetryButton.visibility = View.GONE
        binding.communityRetryButton.isEnabled = true
        binding.communityPostsRecyclerView.visibility = View.VISIBLE
    }

    private fun showCommunityError(message: String) {
        postAdapter.submitPosts(emptyList())
        binding.communityStatusText.visibility = View.VISIBLE
        binding.communityStatusText.text = message
        binding.communityRetryButton.visibility = View.VISIBLE
        binding.communityRetryButton.isEnabled = true
        binding.communityPostsRecyclerView.visibility = View.GONE
    }

    private fun renderRecipeSections() {
        if (cachedRecipes.isEmpty()) {
            showEmpty()
            return
        }

        val featuredRecipe = cachedRecipes.first()
        val recentRecipes = cachedRecipes.drop(1)
        val featuredPostId = findPostIdForRecipe(featuredRecipe.id)

        binding.homeStatusText.visibility = View.GONE
        binding.homeRetryButton.visibility = View.GONE
        binding.homeRetryButton.isEnabled = true
        binding.featuredRecipeCard.visibility = View.VISIBLE
        binding.featuredRecipeCard.isClickable = true
        binding.featuredRecipeCard.isFocusable = true
        binding.featuredRecipeCard.setOnClickListener {
            openRecipeDetails(featuredRecipe.id, featuredPostId)
        }
        binding.featuredRecipeCreatorName.text = findCreatorNameForRecipe(featuredRecipe.id)
        binding.featuredRecipeTitle.text = featuredRecipe.title
        binding.featuredRecipeCookingTime.text = formatCookingTime(featuredRecipe)
        binding.featuredRecipeImage.loadRecipeImage(featuredRecipe.imageUrl)

        binding.recentRecipesContainer.removeAllViews()
        binding.recentRecipesTitle.visibility = View.VISIBLE
        if (recentRecipes.isEmpty()) {
            binding.recentRecipesScroll.visibility = View.GONE
            binding.homeNoRecentRecipes.visibility = View.VISIBLE
        } else {
            binding.recentRecipesScroll.visibility = View.VISIBLE
            binding.homeNoRecentRecipes.visibility = View.GONE
            recentRecipes.forEach { recipe ->
                binding.recentRecipesContainer.addView(
                    createRecipePreviewCard(
                        recipe = recipe,
                        creatorName = findCreatorNameForRecipe(recipe.id),
                        postId = findPostIdForRecipe(recipe.id)
                    )
                )
            }
        }
    }

    private fun createRecipePreviewCard(
        recipe: RecipeResponse,
        creatorName: String,
        postId: Int?
    ): View {
        val cardBinding = ItemCommunityPostBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.recentRecipesContainer,
            false
        )

        cardBinding.communityPostCard.layoutParams = LinearLayout.LayoutParams(
            dp(220),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
        }
        cardBinding.communityPostCard.isClickable = true
        cardBinding.communityPostCard.isFocusable = true
        cardBinding.postCreatorName.text = creatorName
        cardBinding.postTitle.text = recipe.title
        cardBinding.postCookingTime.text = formatCookingTime(recipe)
        cardBinding.postIngredients.visibility = View.GONE
        cardBinding.postCaption.visibility = View.GONE
        cardBinding.postRating.visibility = View.GONE
        cardBinding.postTapHint.visibility = View.VISIBLE
        cardBinding.postActionsRow.visibility = View.GONE
        cardBinding.postRecipeImage.visibility = View.VISIBLE
        cardBinding.postRecipeImage.loadRecipeImage(recipe.imageUrl)
        cardBinding.communityPostCard.setOnClickListener {
            openRecipeDetails(recipe.id, postId)
        }

        return cardBinding.root
    }

    private fun findPostIdForRecipe(recipeId: Int): Int? {
        return cachedPosts.firstOrNull {
            it.recipeId == recipeId
        }?.id
    }

    private fun findCreatorNameForRecipe(recipeId: Int): String {
        return cachedPosts.firstOrNull {
            it.recipeId == recipeId
        }?.creatorName ?: getString(R.string.profile_user_name)
    }

    private fun openRecipeDetails(recipeId: Int, postId: Int?) {
        if (isNavigatingToRecipePost) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.homeFragment) {
            return
        }

        isNavigatingToRecipePost = true
        val args = Bundle().apply {
            putInt(RecipePostDetailFragment.ARG_RECIPE_ID, recipeId)
            if (postId != null && postId > 0) {
                putInt(RecipePostDetailFragment.ARG_POST_ID, postId)
            }
        }

        try {
            navController.navigate(R.id.recipePostDetailFragment, args)
        } catch (_: IllegalArgumentException) {
            isNavigatingToRecipePost = false
        }
    }

    private fun openSearch() {
        if (isNavigatingToSearch) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.homeFragment) {
            return
        }

        isNavigatingToSearch = true
        try {
            navController.navigate(R.id.searchFragment)
        } catch (_: IllegalArgumentException) {
            isNavigatingToSearch = false
        }
    }

    private fun editPost(post: CommunityPostResponse) {
        CommunityPostFormDialogFragment.newInstance(post)
            .show(parentFragmentManager, "CommunityPostFormDialog")
    }

    private fun showComments(post: CommunityPostResponse) {
        CommunityCommentsDialogFragment.newInstance(post.id, post.title)
            .show(parentFragmentManager, "CommunityCommentsDialog")
    }

    private fun confirmDeletePost(post: CommunityPostResponse) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_community_post_title)
            .setMessage(R.string.delete_community_post_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deletePost(post)
            }
            .show()
    }

    private fun deletePost(post: CommunityPostResponse) {
        if (deletePostJob?.isActive == true) {
            return
        }

        binding.communityRetryButton.isEnabled = false
        deletePostJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = postRepository.deleteCommunityPost(post.id)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    loadCommunityPosts()
                    loadRecipes()
                },
                onFailure = { error ->
                    showCommunityError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.generic_error)
                    )
                }
            )
        }
    }

    private fun formatCookingTime(recipe: RecipeResponse): String {
        return recipe.cookingTimeMinutes?.let { "$it minutes" } ?: getString(R.string.no_cooking_time)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadRecipesJob?.cancel()
        loadPostsJob?.cancel()
        deletePostJob?.cancel()
        binding.communityPostsRecyclerView.adapter = null
        _binding = null
    }
}
