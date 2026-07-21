package com.example.crumb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.FragmentSearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val searchAdapter = SearchRecipeAdapter(::openRecipeDetails)
    private var loadSearchJob: Job? = null
    private var debounceJob: Job? = null
    private var currentQuery = ""
    private var isSourceLoaded = false
    private var isNavigatingToDetail = false
    private var searchGeneration = 0
    private var allSearchItems: List<SearchRecipeCardItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.searchResultsRecyclerView.adapter = searchAdapter
        binding.searchBackButton.setOnClickListener { navigateBackToHome() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            navigateBackToHome()
        }
        binding.searchQueryEditText.doAfterTextChanged { text ->
            currentQuery = text?.toString().orEmpty()
            scheduleSearch()
        }
        binding.searchRetryButton.setOnClickListener { loadSearchSource() }
        setFragmentResultListener(RecipeFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadSearchSource()
        }

        currentQuery = savedInstanceState?.getString(KEY_QUERY).orEmpty()
        if (currentQuery.isNotBlank()) {
            binding.searchQueryEditText.setText(currentQuery)
            binding.searchQueryEditText.setSelection(currentQuery.length)
        }

        loadSearchSource()
        focusSearchField()
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToDetail = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_QUERY, currentQuery)
    }

    private fun loadSearchSource() {
        loadSearchJob?.cancel()
        showLoading()

        loadSearchJob = viewLifecycleOwner.lifecycleScope.launch {
            val userRecipesDeferred = async { recipeRepository.getUserRecipes() }
            val communityPostsDeferred = async { postRepository.getCommunityPosts() }

            val userRecipesResult = userRecipesDeferred.await()
            val communityPostsResult = communityPostsDeferred.await()
            if (_binding == null) return@launch

            val userRecipes = userRecipesResult.getOrNull()
            val communityPosts = communityPostsResult.getOrNull()
            if (userRecipes == null && communityPosts == null) {
                showError(
                    userRecipesResult.exceptionOrNull()?.message
                        ?: communityPostsResult.exceptionOrNull()?.message
                        ?: getString(R.string.search_error)
                )
                return@launch
            }

            allSearchItems = buildSearchItems(
                userRecipes.orEmpty(),
                communityPosts.orEmpty()
            )
            isSourceLoaded = true
            val hasAnyData = allSearchItems.isNotEmpty()
            if (hasAnyData) {
                renderCurrentQuery(immediate = true)
            } else if (userRecipesResult.isFailure || communityPostsResult.isFailure) {
                showError(
                    userRecipesResult.exceptionOrNull()?.message
                        ?: communityPostsResult.exceptionOrNull()?.message
                        ?: getString(R.string.search_error)
                )
            } else {
                showNoResults()
            }
        }
    }

    private fun buildSearchItems(
        userRecipes: List<RecipeResponse>,
        communityPosts: List<CommunityPostResponse>
    ): List<SearchRecipeCardItem> {
        val itemsByRecipeId = linkedMapOf<Int, SearchRecipeCardItem>()
        val communityPostsByRecipeId = communityPosts
            .mapNotNull { post ->
                val recipe = post.recipe ?: return@mapNotNull null
                recipe.id to post
            }
            .toMap()

        userRecipes.forEach { recipe ->
            val post = communityPostsByRecipeId[recipe.id]
            itemsByRecipeId[recipe.id] = recipe.toSearchCardItem(
                postId = post?.id,
                creatorName = post?.creatorName ?: recipe.creatorName
            )
        }

        communityPosts.forEach { post ->
            val recipe = post.recipe ?: return@forEach
            val existing = itemsByRecipeId[recipe.id]
            if (existing == null) {
                itemsByRecipeId[recipe.id] = recipe.toSearchCardItem(
                    postId = post.id,
                    creatorName = post.creatorName,
                    createdAt = post.createdAt
                )
            } else {
                itemsByRecipeId[recipe.id] = existing.copy(
                    postId = post.id,
                    creatorName = post.creatorName.ifBlank { existing.creatorName },
                    sortKey = maxOf(existing.sortKey, parseInstant(post.createdAt)?.toEpochMilli() ?: existing.sortKey)
                )
            }
        }

        return itemsByRecipeId.values.sortedByDescending { it.sortKey }
    }

    private fun RecipeResponse.toSearchCardItem(
        postId: Int?,
        creatorName: String?,
        createdAt: String = this.createdAt
    ): SearchRecipeCardItem {
        return SearchRecipeCardItem(
            recipeId = id,
            postId = postId,
            title = title,
            creatorName = creatorName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_user_name),
            cookingTimeText = cookingTimeMinutes?.let { "$it minutes" }
                ?: getString(R.string.no_cooking_time),
            ingredients = ingredients.map { it.ingredientName },
            imageUrl = imageUrl,
            sortKey = parseInstant(createdAt)?.toEpochMilli() ?: 0L
        )
    }

    private fun renderCurrentQuery(immediate: Boolean = false) {
        if (!isSourceLoaded) {
            return
        }

        val normalizedQuery = currentQuery.trim()
        val token = ++searchGeneration

        if (normalizedQuery.isBlank() || immediate) {
            renderFilteredResults(normalizedQuery)
            return
        }

        debounceJob?.cancel()
        debounceJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_MS)
            if (_binding == null || token != searchGeneration) {
                return@launch
            }
            renderFilteredResults(normalizedQuery)
        }
    }

    private fun scheduleSearch() {
        if (!isSourceLoaded) {
            return
        }

        renderCurrentQuery()
    }

    private fun renderFilteredResults(query: String) {
        if (_binding == null) {
            return
        }

        val filtered = if (query.isBlank()) {
            allSearchItems
        } else {
            val normalizedQuery = query.lowercase(Locale.US)
            allSearchItems.filter { item ->
                item.matches(normalizedQuery)
            }
        }

        if (filtered.isEmpty()) {
            showNoResults()
        } else {
            showResults(filtered)
        }
    }

    private fun SearchRecipeCardItem.matches(normalizedQuery: String): Boolean {
        return title.lowercase(Locale.US).contains(normalizedQuery) ||
            creatorName.lowercase(Locale.US).contains(normalizedQuery) ||
            ingredients.any { it.lowercase(Locale.US).contains(normalizedQuery) }
    }

    private fun showLoading() {
        binding.searchStatusText.visibility = View.VISIBLE
        binding.searchStatusText.text = getString(R.string.search_loading)
        binding.searchRetryButton.visibility = View.GONE
        binding.searchRetryButton.isEnabled = false
        binding.searchResultsRecyclerView.visibility = View.GONE
    }

    private fun showResults(items: List<SearchRecipeCardItem>) {
        searchAdapter.submitItems(items)
        binding.searchStatusText.visibility = View.GONE
        binding.searchRetryButton.visibility = View.GONE
        binding.searchRetryButton.isEnabled = true
        binding.searchResultsRecyclerView.visibility = View.VISIBLE
    }

    private fun showNoResults() {
        searchAdapter.submitItems(emptyList())
        binding.searchStatusText.visibility = View.VISIBLE
        binding.searchStatusText.text = getString(R.string.search_no_results)
        binding.searchRetryButton.visibility = View.GONE
        binding.searchRetryButton.isEnabled = true
        binding.searchResultsRecyclerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        searchAdapter.submitItems(emptyList())
        binding.searchStatusText.visibility = View.VISIBLE
        binding.searchStatusText.text = message
        binding.searchRetryButton.visibility = View.VISIBLE
        binding.searchRetryButton.isEnabled = true
        binding.searchResultsRecyclerView.visibility = View.GONE
    }

    private fun focusSearchField() {
        binding.searchQueryEditText.requestFocus()
        binding.searchQueryEditText.post {
            if (_binding == null) {
                return@post
            }

            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.searchQueryEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun openRecipeDetails(item: SearchRecipeCardItem) {
        if (isNavigatingToDetail) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.searchFragment) {
            return
        }

        isNavigatingToDetail = true
        val args = Bundle().apply {
            putInt(RecipePostDetailFragment.ARG_RECIPE_ID, item.recipeId)
            item.postId?.let { postId ->
                putInt(RecipePostDetailFragment.ARG_POST_ID, postId)
            }
        }

        try {
            navController.navigate(R.id.recipePostDetailFragment, args)
        } catch (_: IllegalArgumentException) {
            isNavigatingToDetail = false
        }
    }

    private fun navigateBackToHome() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.searchFragment) {
            return
        }

        if (!navController.popBackStack(R.id.homeFragment, false)) {
            navController.navigate(R.id.homeFragment)
        }
    }

    private fun parseInstant(value: String): Instant? {
        if (value.isBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadSearchJob?.cancel()
        debounceJob?.cancel()
        binding.searchResultsRecyclerView.adapter = null
        _binding = null
    }

    companion object {
        private const val KEY_QUERY = "search_query"
        private const val DEBOUNCE_MS = 350L
    }
}
