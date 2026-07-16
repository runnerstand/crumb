package com.example.crumb

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentHomeBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val postAdapter = CommunityPostAdapter(
        onCommentsClick = ::showComments,
        onEditClick = ::editPost,
        onDeleteClick = ::confirmDeletePost
    )
    private var loadRecipesJob: Job? = null
    private var loadPostsJob: Job? = null
    private var deletePostJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.communityPostsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.communityPostsRecyclerView.adapter = postAdapter
        binding.homeRetryButton.setOnClickListener {
            loadRecipes()
        }
        binding.communityRetryButton.setOnClickListener {
            loadCommunityPosts()
        }
        binding.createPostButton.setOnClickListener {
            CommunityPostFormDialogFragment.newInstance()
                .show(parentFragmentManager, "CommunityPostFormDialog")
        }
        setFragmentResultListener(CommunityPostFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadCommunityPosts()
        }
        loadRecipes()
        loadCommunityPosts()
    }

    private fun loadRecipes() {
        loadRecipesJob?.cancel()
        showLoading()

        loadRecipesJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.getUserRecipes()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        showEmpty()
                    } else {
                        showRecipes(recipes)
                    }
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

    private fun showRecipes(recipes: List<RecipeResponse>) {
        val featuredRecipe = recipes.first()
        val recentRecipes = recipes.drop(1)

        binding.homeStatusText.visibility = View.GONE
        binding.homeRetryButton.visibility = View.GONE
        binding.homeRetryButton.isEnabled = true
        binding.featuredRecipeCard.visibility = View.VISIBLE

        binding.featuredRecipeTitle.text = featuredRecipe.title
        binding.featuredRecipeCookingTime.text = formatCookingTime(featuredRecipe)
        binding.featuredRecipeIngredients.text = formatIngredients(featuredRecipe)
        binding.featuredRecipeViewButton.setOnClickListener {
            showRecipeDetails(featuredRecipe)
        }

        binding.recentRecipesContainer.removeAllViews()
        binding.recentRecipesTitle.visibility = View.VISIBLE
        if (recentRecipes.isEmpty()) {
            binding.recentRecipesScroll.visibility = View.GONE
            binding.homeNoRecentRecipes.visibility = View.VISIBLE
        } else {
            binding.recentRecipesScroll.visibility = View.VISIBLE
            binding.homeNoRecentRecipes.visibility = View.GONE
            recentRecipes.forEach { recipe ->
                binding.recentRecipesContainer.addView(createRecentRecipeCard(recipe))
            }
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
                    if (posts.isEmpty()) {
                        showCommunityEmpty()
                    } else {
                        showCommunityPosts(posts)
                    }
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

    private fun showCommunityLoading() {
        binding.communityStatusText.visibility = View.VISIBLE
        binding.communityStatusText.text = getString(R.string.community_posts_loading)
        binding.communityRetryButton.visibility = View.GONE
        binding.communityRetryButton.isEnabled = false
        binding.communityPostsRecyclerView.visibility = View.GONE
    }

    private fun showCommunityEmpty() {
        postAdapter.submitPosts(emptyList())
        binding.communityStatusText.visibility = View.VISIBLE
        binding.communityStatusText.text = getString(R.string.community_posts_empty)
        binding.communityRetryButton.visibility = View.GONE
        binding.communityRetryButton.isEnabled = true
        binding.communityPostsRecyclerView.visibility = View.GONE
    }

    private fun showCommunityError(message: String) {
        postAdapter.submitPosts(emptyList())
        binding.communityStatusText.visibility = View.VISIBLE
        binding.communityStatusText.text = message
        binding.communityRetryButton.visibility = View.VISIBLE
        binding.communityRetryButton.isEnabled = true
        binding.communityPostsRecyclerView.visibility = View.GONE
    }

    private fun showCommunityPosts(posts: List<CommunityPostResponse>) {
        postAdapter.submitPosts(posts)
        binding.communityStatusText.visibility = View.GONE
        binding.communityRetryButton.visibility = View.GONE
        binding.communityRetryButton.isEnabled = true
        binding.communityPostsRecyclerView.visibility = View.VISIBLE
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
                onSuccess = { loadCommunityPosts() },
                onFailure = { error ->
                    showCommunityError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.generic_error)
                    )
                }
            )
        }
    }

    private fun createRecentRecipeCard(recipe: RecipeResponse): View {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
            radius = resources.getDimension(R.dimen.card_radius)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.divider_color)
            setOnClickListener {
                showRecipeDetails(recipe)
            }
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val placeholder = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(100)
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.primary_orange_light))
        }
        val textContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_medium),
                resources.getDimensionPixelSize(R.dimen.spacing_medium),
                resources.getDimensionPixelSize(R.dimen.spacing_medium),
                resources.getDimensionPixelSize(R.dimen.spacing_medium)
            )
        }
        val title = MaterialTextView(context).apply {
            text = recipe.title
            setTextColor(ContextCompat.getColor(context, R.color.text_dark_brown))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val cookingTime = MaterialTextView(context).apply {
            text = formatCookingTime(recipe)
            setTextColor(ContextCompat.getColor(context, R.color.text_muted_brown))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }

        textContent.addView(title)
        textContent.addView(cookingTime)
        cardContent.addView(placeholder)
        cardContent.addView(textContent)
        card.addView(cardContent)
        return card
    }

    private fun showRecipeDetails(recipe: RecipeResponse) {
        val ingredients = recipe.ingredients.joinToString("\n") {
            val measurement = listOfNotNull(it.quantity, it.unit)
                .joinToString(" ")
                .takeIf { value -> value.isNotBlank() }
            if (measurement == null) it.ingredientName else "${it.ingredientName} - $measurement"
        }
        val message = buildString {
            appendLine(formatCookingTime(recipe))
            appendLine()
            appendLine("Ingredients")
            appendLine(ingredients.ifBlank { "No ingredients" })
            appendLine()
            appendLine("Instructions")
            append(recipe.instructions.joinToString("\n").ifBlank { "No instructions" })
        }

        AlertDialog.Builder(requireContext())
            .setTitle(recipe.title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatCookingTime(recipe: RecipeResponse): String {
        return recipe.cookingTimeMinutes?.let { "$it minutes" } ?: "No cooking time"
    }

    private fun formatIngredients(recipe: RecipeResponse): String {
        val ingredients = recipe.ingredients.joinToString(", ") { it.ingredientName }
        return if (ingredients.isBlank()) {
            "Ingredients: No ingredients"
        } else {
            "Ingredients: $ingredients"
        }
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
