package com.example.crumb

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.CommunityCommentRepository
import com.example.crumb.data.CommunityCommentRequest
import com.example.crumb.data.CommunityCommentResponse
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.data.RecipeIngredientResponse
import com.example.crumb.data.RecipeRepository
import com.example.crumb.data.RecipeResponse
import com.example.crumb.data.SavedRecipeRepository
import com.example.crumb.databinding.FragmentRecipePostDetailBinding
import com.example.crumb.databinding.ItemRecipePostDirectionBinding
import com.example.crumb.databinding.ItemRecipePostIngredientBinding
import com.example.crumb.databinding.ItemRecipeRecommendationBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

class RecipePostDetailFragment : Fragment() {

    private var _binding: FragmentRecipePostDetailBinding? = null
    private val binding get() = _binding!!
    private val recipeRepository = RecipeRepository()
    private val postRepository = CommunityPostRepository()
    private val commentRepository = CommunityCommentRepository()
    private val savedRecipeRepository = SavedRecipeRepository()
    private val commentAdapter = CommunityCommentAdapter(
        onEditClick = ::editComment,
        onDeleteClick = ::confirmDeleteComment
    )
    private var loadRecipeJob: Job? = null
    private var loadCommentsJob: Job? = null
    private var submitCommentJob: Job? = null
    private var deleteCommentJob: Job? = null
    private var deleteRecipeJob: Job? = null
    private var savedStateJob: Job? = null
    private var toggleSavedJob: Job? = null
    private var loadRecommendationsJob: Job? = null
    private var currentRecipe: RecipeResponse? = null
    private var currentCommentsPostId: Int = NO_POST_ID
    private var currentUserRating: Int = 0
    private var servings = 2
    private var isMetric = true
    private var isSaved = false
    private var isNavigatingToRecommendation = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipePostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setFragmentResultListener(RecipeFormDialogFragment.REQUEST_KEY) { _, _ ->
            loadRecipe()
        }
        childFragmentManager.setFragmentResultListener(
            CommunityCommentFormDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            loadRecipe()
        }
        setupListeners()
        setupRecommendations()
        setupComments()
        setupRatingControls()
        loadRecipe()
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToRecommendation = false
    }

    private fun loadRecipe() {
        val recipeId = arguments?.getInt(ARG_RECIPE_ID, NO_POST_ID) ?: NO_POST_ID
        if (recipeId <= 0) {
            Toast.makeText(requireContext(), R.string.generic_error, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val postId = arguments?.getInt(ARG_POST_ID, NO_POST_ID) ?: NO_POST_ID
        currentCommentsPostId = postId

        loadRecipeJob?.cancel()
        loadRecipeJob = viewLifecycleOwner.lifecycleScope.launch {
            val linkedPost = if (postId > 0) {
                postRepository.getCommunityPosts().getOrNull()
                    ?.firstOrNull { post ->
                        post.id == postId && (post.recipeId == recipeId || post.recipe?.id == recipeId)
                    }
            } else {
                null
            }
            val result = linkedPost?.recipe?.let { Result.success(it) }
                ?: recipeRepository.getRecipe(recipeId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { recipe ->
                    currentRecipe = recipe
                    bindRecipe(recipe, linkedPost)
                    loadCommentsForCurrentPost()
                    loadSavedState(recipe.id)
                    loadRecommendations(recipe.id)
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), error.message ?: getString(R.string.recipes_error), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun bindRecipe(recipe: RecipeResponse, post: CommunityPostResponse?) {
        binding.recipeTitle.text = recipe.title
        binding.creatorName.text = post?.creatorName
            ?: recipe.creatorName
            ?: getString(R.string.profile_user_name)
        binding.creatorSubtitle.text = getString(R.string.community_post_creator_subtitle)
        binding.sharingCaption.visibility = View.GONE
        binding.recipeRating.text = formatRecipeRating(recipe.averageRating, recipe.ratingCount)
        binding.cookingTime.text = recipe.cookingTimeMinutes?.let { "$it mins" }
            ?: getString(R.string.no_cooking_time)
        binding.servingCount.text = servings.toString()
        currentUserRating = recipe.userRating?.coerceIn(0, 5) ?: 0
        updateRatingSelection(currentUserRating)

        renderIngredients(recipe.ingredients)
        renderDirections(recipe.instructions)
        updateOwnerActions(recipe)
        applySavedState(isSaved)
    }

    private fun loadCommentsForCurrentPost() {
        val postId = currentCommentsPostId

        if (postId <= 0) {
            showUnpublishedCommentsState()
            return
        }

        loadComments(postId)
    }

    private fun loadComments(postId: Int) {
        loadCommentsJob?.cancel()
        showCommentStatus(getString(R.string.community_comments_loading), false, showRetry = false)

        loadCommentsJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = commentRepository.getCommunityComments(postId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { comments ->
                    if (comments.isEmpty()) {
                        showEmptyCommentsState()
                    } else {
                        showComments(comments)
                    }
                },
                onFailure = { error ->
                    showCommentStatus(
                        error.message ?: getString(R.string.community_comments_error),
                        true,
                        true
                    )
                }
            )
        }
    }

    private fun showUnpublishedCommentsState() {
        binding.commentsHeading.text = getString(R.string.community_comments_title)
        binding.commentsStatusText.text = getString(R.string.recipe_comments_unpublished)
        binding.commentsStatusText.visibility = View.VISIBLE
        binding.commentsRetryButton.visibility = View.GONE
        binding.commentsList.visibility = View.GONE
        binding.commentInput.visibility = View.GONE
        binding.commentRatingContainer.visibility = View.GONE
        binding.sendCommentButton.visibility = View.GONE
    }

    private fun showEmptyCommentsState() {
        binding.commentsHeading.text = getString(R.string.community_comments_count, 0)
        binding.commentsStatusText.text = getString(R.string.community_comments_empty_hint)
        binding.commentsStatusText.visibility = View.VISIBLE
        binding.commentsRetryButton.visibility = View.GONE
        binding.commentsList.visibility = View.GONE
        binding.commentInput.visibility = View.VISIBLE
        binding.commentRatingContainer.visibility = View.VISIBLE
        binding.sendCommentButton.visibility = View.VISIBLE
        commentAdapter.submitComments(emptyList())
    }

    private fun showComments(comments: List<CommunityCommentResponse>) {
        binding.commentsHeading.text = getString(
            R.string.community_comments_count,
            comments.size
        )
        binding.commentsStatusText.visibility = View.GONE
        binding.commentsRetryButton.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
        binding.commentInput.visibility = View.VISIBLE
        binding.commentRatingContainer.visibility = View.VISIBLE
        binding.sendCommentButton.visibility = View.VISIBLE
        commentAdapter.submitComments(comments)
    }

    private fun showCommentStatus(message: String, isError: Boolean, showRetry: Boolean = false) {
        binding.commentsHeading.text = getString(R.string.community_comments_title)
        binding.commentsStatusText.text = message
        binding.commentsStatusText.visibility = View.VISIBLE
        binding.commentsRetryButton.visibility = if (showRetry) View.VISIBLE else View.GONE
        binding.commentsRetryButton.setOnClickListener {
            if (currentCommentsPostId > 0) {
                loadComments(currentCommentsPostId)
            }
        }
        binding.commentsList.visibility = View.GONE
        binding.commentRatingContainer.visibility = if (currentCommentsPostId > 0) View.VISIBLE else View.GONE
        binding.commentInput.visibility = if (currentCommentsPostId > 0) View.VISIBLE else View.GONE
        binding.sendCommentButton.visibility = if (currentCommentsPostId > 0) View.VISIBLE else View.GONE
    }

    private fun setupComments() {
        binding.commentsList.layoutManager = LinearLayoutManager(requireContext())
        binding.commentsList.adapter = commentAdapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.bookmarkButton.setOnClickListener {
            toggleSavedState()
        }

        binding.saveRecipeButton.setOnClickListener {
            toggleSavedState()
        }

        binding.decreaseServingsButton.setOnClickListener {
            if (servings > 1) {
                servings--
                binding.servingCount.text = servings.toString()
                currentRecipe?.let { renderIngredients(it.ingredients) }
            }
        }

        binding.increaseServingsButton.setOnClickListener {
            servings++
            binding.servingCount.text = servings.toString()
            currentRecipe?.let { renderIngredients(it.ingredients) }
        }

        binding.measurementToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isMetric = checkedId == R.id.metric_toggle
                currentRecipe?.let { renderIngredients(it.ingredients) }
            }
        }

        binding.sendCommentButton.setOnClickListener {
            submitComment()
        }
        binding.editRecipeButton.setOnClickListener {
            editCurrentRecipe()
        }
        binding.deleteRecipeButton.setOnClickListener {
            confirmDeleteCurrentRecipe()
        }
    }

    private fun updateOwnerActions(recipe: RecipeResponse) {
        val isOwner = recipe.userId == LOCAL_USER_ID
        binding.ownerRecipeActions.visibility = if (isOwner) View.VISIBLE else View.GONE
        binding.editRecipeButton.isEnabled = isOwner
        binding.deleteRecipeButton.isEnabled = isOwner
    }

    private fun loadSavedState(recipeId: Int) {
        savedStateJob?.cancel()
        savedStateJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = savedRecipeRepository.getSavedRecipeStatus(recipeId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { status ->
                    applySavedState(status.isSaved)
                },
                onFailure = {
                    applySavedState(isSaved)
                }
            )
        }
    }

    private fun applySavedState(saved: Boolean) {
        isSaved = saved
        val iconRes = if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_border
        val buttonText = if (saved) getString(R.string.remove_from_saved) else getString(R.string.save_this_recipe)
        val buttonContentDescription = if (saved) {
            getString(R.string.remove_from_saved)
        } else {
            getString(R.string.save_this_recipe)
        }

        binding.bookmarkButton.setIconResource(iconRes)
        binding.bookmarkButton.contentDescription = buttonContentDescription
        binding.saveRecipeButton.text = buttonText
        binding.saveRecipeButton.setIconResource(iconRes)
        binding.saveRecipeButton.contentDescription = buttonContentDescription
        binding.saveRecipeButton.setBackgroundColor(
            resources.getColor(
                if (saved) R.color.accent_green else R.color.primary_orange,
                null
            )
        )
    }

    private fun setSavedControlsEnabled(enabled: Boolean) {
        binding.bookmarkButton.isEnabled = enabled
        binding.saveRecipeButton.isEnabled = enabled
    }

    private fun editCurrentRecipe() {
        val recipe = currentRecipe ?: return
        if (!binding.editRecipeButton.isEnabled) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.recipePostDetailFragment) {
            return
        }

        val args = Bundle().apply {
            putInt(EditRecipeFragment.ARG_RECIPE_ID, recipe.id)
        }
        try {
            navController.navigate(R.id.editRecipeFragment, args)
        } catch (_: IllegalArgumentException) {
            if (_binding != null) {
                binding.editRecipeButton.isEnabled = true
            }
        }
    }

    private fun confirmDeleteCurrentRecipe() {
        val recipe = currentRecipe ?: return
        if (deleteRecipeJob?.isActive == true || !binding.deleteRecipeButton.isEnabled) {
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_recipe_title)
            .setMessage(R.string.delete_recipe_with_publication_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteRecipe(recipe)
            }
            .show()
    }

    private fun deleteRecipe(recipe: RecipeResponse) {
        if (deleteRecipeJob?.isActive == true) {
            return
        }

        binding.deleteRecipeButton.isEnabled = false
        binding.editRecipeButton.isEnabled = false
        deleteRecipeJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = recipeRepository.deleteUserRecipe(recipe.id)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    setFragmentResult(
                        RecipeFormDialogFragment.REQUEST_KEY,
                        Bundle().apply { putBoolean(RecipeFormDialogFragment.RESULT_CHANGED, true) }
                    )
                    Toast.makeText(requireContext(), R.string.recipe_deleted, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.homeFragment, false)
                },
                onFailure = { error ->
                    binding.deleteRecipeButton.isEnabled = true
                    binding.editRecipeButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.generic_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun submitComment() {
        val postId = currentCommentsPostId
        if (postId <= 0) {
            return
        }

        val text = binding.commentInput.text?.toString().orEmpty().trim()
        if (text.isBlank()) {
            return
        }

        if (submitCommentJob?.isActive == true) {
            return
        }

        submitCommentJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = commentRepository.createCommunityComment(
                postId,
                CommunityCommentRequest(
                    commentText = text,
                    rating = getSelectedCommentRating()
                )
            )
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    binding.commentInput.text?.clear()
                    loadRecipe()
                },
                onFailure = { error ->
                    showCommentStatus(error.message ?: getString(R.string.community_comments_error), true, true)
                }
            )
        }
    }

    private fun editComment(comment: CommunityCommentResponse) {
        CommunityCommentFormDialogFragment.newInstance(
            currentCommentsPostId,
            comment,
            getSelectedCommentRating() ?: 0
        ).show(childFragmentManager, COMMENT_FORM_DIALOG_TAG)
    }

    private fun confirmDeleteComment(comment: CommunityCommentResponse) {
        if (deleteCommentJob?.isActive == true) {
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_community_comment_title)
            .setMessage(R.string.delete_community_comment_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteComment(comment)
            }
            .show()
    }

    private fun deleteComment(comment: CommunityCommentResponse) {
        if (deleteCommentJob?.isActive == true) {
            return
        }

        deleteCommentJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = commentRepository.deleteCommunityComment(comment.id)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    loadRecipe()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.generic_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun getSelectedCommentRating(): Int? {
        return currentUserRating.takeIf { it in 1..5 }
    }

    private fun setupRatingControls() {
        ratingButtons().forEachIndexed { index, button ->
            button.setOnClickListener {
                currentUserRating = index + 1
                updateRatingSelection(currentUserRating)
            }
        }
        updateRatingSelection(currentUserRating)
    }

    private fun updateRatingSelection(selectedRating: Int) {
        ratingButtons().forEachIndexed { index, button ->
            val isSelected = index + 1 <= selectedRating
            button.iconTint = ContextCompat.getColorStateList(
                requireContext(),
                if (isSelected) R.color.primary_orange else R.color.text_muted_brown
            )
            button.isSelected = isSelected
        }
    }

    private fun ratingButtons(): List<com.google.android.material.button.MaterialButton> {
        return listOf(
            binding.commentRatingStar1,
            binding.commentRatingStar2,
            binding.commentRatingStar3,
            binding.commentRatingStar4,
            binding.commentRatingStar5
        )
    }

    private fun formatRecipeRating(averageRating: Double?, ratingCount: Int): String {
        if (averageRating == null || ratingCount <= 0) {
            return getString(R.string.no_ratings_yet)
        }

        val averageText = String.format(Locale.US, "%.1f", averageRating)
        val countLabel = if (ratingCount == 1) {
            "1 rating"
        } else {
            "$ratingCount ratings"
        }
        return getString(R.string.recipe_rating_summary, averageText, countLabel)
    }

    private fun renderIngredients(ingredients: List<RecipeIngredientResponse>) {
        binding.ingredientsList.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val scalingFactor = servings / 2.0

        ingredients.forEach { ingredient ->
            val itemBinding = ItemRecipePostIngredientBinding.inflate(inflater, binding.ingredientsList, false)
            itemBinding.ingredientName.text = ingredient.ingredientName
            itemBinding.ingredientQuantityUnit.text = formatIngredientQuantity(ingredient, scalingFactor)
            binding.ingredientsList.addView(itemBinding.root)
        }
    }

    private fun formatIngredientQuantity(
        ingredient: RecipeIngredientResponse,
        scalingFactor: Double
    ): String {
        val quantity = ingredient.quantity?.trim().orEmpty()
        val unit = ingredient.unit.orEmpty().trim()
        if (quantity.isBlank()) {
            return unit
        }

        val scaledQuantity = quantity.toDoubleOrNull()?.let { formatQuantity(it * scalingFactor) } ?: quantity
        return listOf(scaledQuantity, unit).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun formatQuantity(qty: Double): String {
        return if (qty % 1.0 == 0.0) {
            qty.toInt().toString()
        } else {
            String.format("%.1f", qty)
        }
    }

    private fun renderDirections(directions: List<String>) {
        binding.directionsList.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        directions.forEachIndexed { index, step ->
            val itemBinding = ItemRecipePostDirectionBinding.inflate(inflater, binding.directionsList, false)
            itemBinding.directionStepNumber.text = (index + 1).toString()
            itemBinding.directionText.text = step
            binding.directionsList.addView(itemBinding.root)
        }
    }

    private fun formatCommentTime(createdAt: String): String {
        val commentDate = parseDate(createdAt) ?: return ""
        val today = LocalDate.now()
        val daysAgo = ChronoUnit.DAYS.between(commentDate, today).coerceAtLeast(0)
        return when (daysAgo) {
            0L -> getString(R.string.comment_today)
            1L -> getString(R.string.comment_one_day_ago)
            else -> getString(R.string.comment_days_ago, daysAgo)
        }
    }

    private fun setupRecommendations() {
        binding.recommendationsList.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.recommendationsList.adapter = RecommendationsAdapter(::openRecommendation)
        binding.recommendationsStatusText.visibility = View.GONE
    }

    private fun loadRecommendations(currentRecipeId: Int) {
        loadRecommendationsJob?.cancel()
        binding.recommendationsStatusText.text = getString(R.string.recommendations_loading)
        binding.recommendationsStatusText.visibility = View.VISIBLE
        binding.recommendationsList.visibility = View.GONE

        loadRecommendationsJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = postRepository.getCommunityPosts()
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { posts ->
                    val recommendations = posts
                        .filter { post ->
                            val linkedRecipeId = post.recipeId ?: post.recipe?.id
                            linkedRecipeId != null && linkedRecipeId != currentRecipeId
                        }
                        .sortedByDescending { parseInstant(it.createdAt) ?: Instant.EPOCH }
                    if (recommendations.isEmpty()) {
                        showEmptyRecommendations()
                    } else {
                        showRecommendations(recommendations)
                    }
                },
                onFailure = { error ->
                    showRecommendationError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.community_posts_error)
                    )
                }
            )
        }
    }

    private fun showRecommendations(posts: List<CommunityPostResponse>) {
        (binding.recommendationsList.adapter as? RecommendationsAdapter)?.submitPosts(posts)
        binding.recommendationsStatusText.visibility = View.GONE
        binding.recommendationsList.visibility = View.VISIBLE
    }

    private fun showEmptyRecommendations() {
        (binding.recommendationsList.adapter as? RecommendationsAdapter)?.submitPosts(emptyList())
        binding.recommendationsStatusText.text = getString(R.string.recommendations_empty)
        binding.recommendationsStatusText.visibility = View.VISIBLE
        binding.recommendationsList.visibility = View.GONE
    }

    private fun showRecommendationError(message: String) {
        (binding.recommendationsList.adapter as? RecommendationsAdapter)?.submitPosts(emptyList())
        binding.recommendationsStatusText.text = message
        binding.recommendationsStatusText.visibility = View.VISIBLE
        binding.recommendationsList.visibility = View.GONE
    }

    private fun openRecommendation(post: CommunityPostResponse) {
        if (isNavigatingToRecommendation) {
            return
        }

        val recipeId = post.recipeId ?: post.recipe?.id ?: return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.recipePostDetailFragment) {
            return
        }

        isNavigatingToRecommendation = true
        val args = Bundle().apply {
            putInt(ARG_RECIPE_ID, recipeId)
            if (post.id > 0) {
                putInt(ARG_POST_ID, post.id)
            }
        }

        try {
            navController.navigate(R.id.recipePostDetailFragment, args)
        } catch (_: IllegalArgumentException) {
            isNavigatingToRecommendation = false
        }
    }

    private fun toggleSavedState() {
        val recipe = currentRecipe ?: return
        if (toggleSavedJob?.isActive == true) {
            return
        }

        val nextSavedState = !isSaved
        applySavedState(nextSavedState)
        setSavedControlsEnabled(false)

        toggleSavedJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = if (nextSavedState) {
                savedRecipeRepository.saveRecipe(recipe.id)
            } else {
                savedRecipeRepository.unsaveRecipe(recipe.id)
            }

            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    applySavedState(nextSavedState)
                    setSavedControlsEnabled(true)
                    Toast.makeText(
                        requireContext(),
                        if (nextSavedState) {
                            getString(R.string.recipe_saved)
                        } else {
                            getString(R.string.recipe_removed_from_saved)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { error ->
                    applySavedState(!nextSavedState)
                    setSavedControlsEnabled(true)
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

    override fun onDestroyView() {
        super.onDestroyView()
        loadRecipeJob?.cancel()
        loadCommentsJob?.cancel()
        submitCommentJob?.cancel()
        deleteCommentJob?.cancel()
        deleteRecipeJob?.cancel()
        savedStateJob?.cancel()
        toggleSavedJob?.cancel()
        loadRecommendationsJob?.cancel()
        binding.commentsList.adapter = null
        _binding = null
    }

    companion object {
        const val ARG_POST_ID = "postId"
        const val ARG_RECIPE_ID = "recipeId"
        private const val NO_POST_ID = -1
        private const val LOCAL_USER_ID = "local-user"
        private const val RECIPE_FORM_DIALOG_TAG = "RecipeFormDialog"
        private const val COMMENT_FORM_DIALOG_TAG = "CommentFormDialog"
    }

    private class RecommendationsAdapter(
        private val onClick: (CommunityPostResponse) -> Unit
    ) : RecyclerView.Adapter<RecommendationsAdapter.ViewHolder>() {
        private val items = mutableListOf<CommunityPostResponse>()

        class ViewHolder(val binding: ItemRecipeRecommendationBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRecipeRecommendationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val post = items[position]
            val recipe = post.recipe
            holder.binding.recTitle.text = recipe?.title ?: post.title
            holder.binding.recCreator.text = "by ${post.creatorName}"
            holder.binding.recTime.text = recipe?.cookingTimeMinutes?.let { "$it mins" }
                ?: holder.binding.root.context.getString(R.string.no_cooking_time)
            holder.binding.recommendationCard.setOnClickListener { onClick(post) }
        }

        override fun getItemCount(): Int = items.size

        fun submitPosts(posts: List<CommunityPostResponse>) {
            items.clear()
            items.addAll(posts)
            notifyDataSetChanged()
        }
    }

    private fun parseDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return parseInstant(value)?.atZone(ZoneId.systemDefault())?.toLocalDate()
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
}
