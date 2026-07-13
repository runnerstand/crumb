package com.example.crumb.feature.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.R
import com.example.crumb.databinding.DialogCommentsBinding
import com.example.crumb.databinding.FragmentHomeBinding
import com.example.crumb.ui.adapter.CommentAdapter
import com.example.crumb.ui.adapter.CommunityPostAdapter
import com.example.crumb.ui.screens.comments.CommentsViewModel
import com.example.crumb.ui.screens.comments.CommunityCommentUiState
import com.example.crumb.ui.screens.home.CommunityPostUiModel
import com.example.crumb.ui.screens.home.CommunityPostUiState
import com.example.crumb.ui.screens.home.HomeDashboardUiState
import com.example.crumb.ui.screens.home.HomeViewModel
import com.example.crumb.ui.screens.recipes.RecipeUiModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var postAdapter: CommunityPostAdapter
    private var commentsDialog: Dialog? = null
    private var dailyRecipe: RecipeUiModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postAdapter = CommunityPostAdapter(
            onOpenComments = ::showCommentsDialog,
            onOpenRecipe = ::showRecipeDetails,
            onUpdatePost = viewModel::updatePost,
            onDeletePost = ::confirmDeletePost
        )
        binding.postsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.postsRecyclerView.adapter = postAdapter
        binding.retryButton.setOnClickListener { viewModel.loadPosts() }
        binding.searchRecipesButton.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }
        binding.addRecipeButton.setOnClickListener {
            findNavController().navigate(
                R.id.recipesFragment,
                Bundle().apply { putBoolean("open_create_recipe", true) }
            )
        }
        binding.createPostButton.setOnClickListener {
            findNavController().navigate(R.id.createPostFragment)
        }
        binding.savedRecipesButton.setOnClickListener {
            findNavController().navigate(R.id.savedFragment)
        }
        binding.viewDailyRecipeButton.setOnClickListener {
            dailyRecipe?.let(::showRecipeDetails)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderPosts) }
                launch { viewModel.dashboardUiState.collect(::renderDashboard) }
                launch { viewModel.recipes.collect { postAdapter.submitRecipes(it) } }
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

    private fun renderDashboard(state: HomeDashboardUiState) = with(binding) {
        when (state) {
            HomeDashboardUiState.Loading -> {
                dailyRecipeTitleTextView.text = "Loading recipe ideas..."
                dailyRecipeMetaTextView.text = ""
                dailyRecipeIngredientsTextView.text = ""
                viewDailyRecipeButton.visibility = View.GONE
                categoryChipGroup.removeAllViews()
            }
            is HomeDashboardUiState.Error -> {
                dailyRecipeTitleTextView.text = "No recipe suggestion yet"
                dailyRecipeMetaTextView.text = ""
                dailyRecipeIngredientsTextView.text = state.message
                viewDailyRecipeButton.visibility = View.GONE
                categoryChipGroup.removeAllViews()
            }
            is HomeDashboardUiState.Success -> {
                dailyRecipe = state.dailyRecipe
                renderDailyRecipe(state.dailyRecipe)
                renderCategoryChips(state.categories)
            }
        }
    }

    private fun renderDailyRecipe(recipe: RecipeUiModel?) = with(binding) {
        if (recipe == null) {
            dailyRecipeTitleTextView.text = "Create a recipe to get a daily idea"
            dailyRecipeMetaTextView.text = ""
            dailyRecipeIngredientsTextView.text =
                "Your first user-created recipe will appear here as a cooking suggestion."
            viewDailyRecipeButton.visibility = View.GONE
            return
        }

        dailyRecipeTitleTextView.text = recipe.title
        dailyRecipeMetaTextView.text = listOfNotNull(
            recipe.cookingTimeMinutes?.let { "$it min" },
            "${recipe.ingredients.size} ingredients"
        ).joinToString(" | ")
        dailyRecipeIngredientsTextView.text = recipe.ingredients
            .take(5)
            .joinToString(", ") { it.displayText() }
        viewDailyRecipeButton.visibility = View.VISIBLE
    }

    private fun renderCategoryChips(categories: List<String>) = with(binding.categoryChipGroup) {
        removeAllViews()
        if (categories.isEmpty()) {
            addView(Chip(requireContext()).apply {
                text = "No categories yet"
                isEnabled = false
            })
            return
        }

        categories.forEach { category ->
            addView(Chip(requireContext()).apply {
                text = category.replaceFirstChar { it.uppercase() }
                setOnClickListener {
                    findNavController().navigate(R.id.searchFragment)
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPosts()
    }

    private fun renderPosts(state: CommunityPostUiState) = with(binding) {
        progressBar.visibility = if (state is CommunityPostUiState.Loading) View.VISIBLE else View.GONE
        postsRecyclerView.visibility = if (state is CommunityPostUiState.Success) View.VISIBLE else View.GONE
        retryButton.visibility =
            if (state is CommunityPostUiState.Error || state is CommunityPostUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }
        statusTextView.visibility =
            if (state is CommunityPostUiState.Error || state is CommunityPostUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (state) {
            CommunityPostUiState.Loading -> statusTextView.text = ""
            CommunityPostUiState.Empty -> {
                statusTextView.text = "No community posts yet."
                postAdapter.submitList(emptyList())
            }
            is CommunityPostUiState.Error -> {
                statusTextView.text = state.message
                postAdapter.submitList(emptyList())
            }
            is CommunityPostUiState.Success -> postAdapter.submitList(state.posts)
        }
    }

    private fun confirmDeletePost(post: CommunityPostUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete post?")
            .setMessage("This will also remove its comments.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> viewModel.deletePost(post.id) }
            .show()
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

    private fun showCommentsDialog(post: CommunityPostUiModel) {
        commentsDialog?.dismiss()
        val dialogBinding = DialogCommentsBinding.inflate(layoutInflater)
        val commentsViewModel = ViewModelProvider(
            this,
            CommentsViewModelFactory(post.id)
        ).get("comments-${post.id}", CommentsViewModel::class.java)
        val adapter = CommentAdapter(
            onUpdateComment = commentsViewModel::updateComment,
            onDeleteComment = { comment ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete comment?")
                    .setMessage("This comment will be removed from the post.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        commentsViewModel.deleteComment(comment.id)
                    }
                    .show()
            }
        )

        dialogBinding.commentsTitleTextView.text = "Comments: ${post.title}"
        dialogBinding.commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.commentsRecyclerView.adapter = adapter
        dialogBinding.postCommentButton.setOnClickListener {
            commentsViewModel.createComment(dialogBinding.newCommentEditText.text.toString())
            dialogBinding.newCommentEditText.setText("")
        }

        commentsDialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()
            .also {
                it.setOnDismissListener { commentsDialog = null }
                it.show()
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    commentsViewModel.uiState.collect { state ->
                        dialogBinding.commentsProgressBar.visibility =
                            if (state is CommunityCommentUiState.Loading) View.VISIBLE else View.GONE
                        dialogBinding.commentsRecyclerView.visibility =
                            if (state is CommunityCommentUiState.Success) View.VISIBLE else View.GONE
                        dialogBinding.commentsStatusTextView.visibility =
                            if (state is CommunityCommentUiState.Error ||
                                state is CommunityCommentUiState.Empty
                            ) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                        when (state) {
                            CommunityCommentUiState.Loading -> dialogBinding.commentsStatusTextView.text = ""
                            CommunityCommentUiState.Empty -> {
                                dialogBinding.commentsStatusTextView.text = "No comments yet."
                                adapter.submitList(emptyList())
                            }
                            is CommunityCommentUiState.Error -> {
                                dialogBinding.commentsStatusTextView.text = state.message
                                adapter.submitList(emptyList())
                            }
                            is CommunityCommentUiState.Success -> adapter.submitList(state.comments)
                        }
                    }
                }
                launch {
                    commentsViewModel.operationMessage.collect { message ->
                        message ?: return@collect
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        commentsViewModel.clearOperationMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        commentsDialog?.dismiss()
        commentsDialog = null
        binding.postsRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private class CommentsViewModelFactory(
    private val postId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CommentsViewModel(postId = postId) as T
    }
}
