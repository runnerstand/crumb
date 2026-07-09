package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemCommunityPostBinding
import com.example.crumb.ui.screens.home.CommunityPostUiModel
import com.example.crumb.ui.screens.recipes.RecipeUiModel

class CommunityPostAdapter(
    private val onOpenComments: (CommunityPostUiModel) -> Unit,
    private val onOpenRecipe: (RecipeUiModel) -> Unit,
    private val onUpdatePost: (Int, String, List<String>, String, Int?) -> Unit,
    private val onDeletePost: (CommunityPostUiModel) -> Unit
) : ListAdapter<CommunityPostUiModel, CommunityPostAdapter.PostViewHolder>(DiffCallback) {
    private val editingPostIds = mutableSetOf<Int>()
    private var recipes: List<RecipeUiModel> = emptyList()

    fun submitRecipes(recipes: List<RecipeUiModel>) {
        this.recipes = recipes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemCommunityPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position), editingPostIds.contains(getItem(position).id))
    }

    inner class PostViewHolder(
        private val binding: ItemCommunityPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: CommunityPostUiModel, isEditing: Boolean) = with(binding) {
            displayContainer.visibility = if (isEditing) View.GONE else View.VISIBLE
            editContainer.visibility = if (isEditing) View.VISIBLE else View.GONE

            postTitleTextView.text = post.title
            postCreatorTextView.text = "By ${post.creatorName}"
            postIngredientsTextView.text = "Ingredients: ${post.ingredients.joinToString(", ")}"
            linkedRecipeTextView.visibility = if (post.recipeId == null) View.GONE else View.VISIBLE
            linkedRecipeTextView.text = when {
                post.linkedRecipe != null -> {
                    val creator = if (post.linkedRecipe.isOwnedByLocalUser) {
                        "Local User"
                    } else {
                        post.linkedRecipe.userId
                    }
                    "Recipe: ${post.linkedRecipe.title}\nCreator: $creator"
                }
                post.recipeId != null -> "Recipe linked: #${post.recipeId}"
                else -> ""
            }
            linkedRecipeTextView.setOnClickListener {
                post.linkedRecipe?.let(onOpenRecipe)
            }
            linkedRecipeTextView.isEnabled = post.linkedRecipe != null
            postCaptionTextView.text = post.caption
            postCaptionTextView.visibility = if (post.caption.isBlank()) View.GONE else View.VISIBLE
            postDatesTextView.text = "Created ${post.createdAt} | Updated ${post.updatedAt}"
            editButton.visibility = if (post.isOwnedByLocalUser) View.VISIBLE else View.GONE
            deleteButton.visibility = if (post.isOwnedByLocalUser) View.VISIBLE else View.GONE

            commentsButton.setOnClickListener { onOpenComments(post) }
            editButton.setOnClickListener {
                editingPostIds.add(post.id)
                notifyItemChanged(bindingAdapterPosition)
            }
            deleteButton.setOnClickListener { onDeletePost(post) }

            if (isEditing) {
                editTitleEditText.setText(post.title)
                editIngredientsEditText.setText(post.ingredients.joinToString(", "))
                editCaptionEditText.setText(post.caption)
                editValidationTextView.text = ""
                var selectedRecipeId = post.recipeId
                val recipeLabels = listOf("No linked recipe") + recipes.map { it.title }
                editRecipeAutoCompleteTextView.setAdapter(
                    ArrayAdapter(
                        itemView.context,
                        android.R.layout.simple_dropdown_item_1line,
                        recipeLabels
                    )
                )
                val selectedRecipeIndex = recipes.indexOfFirst { it.id == selectedRecipeId }
                editRecipeAutoCompleteTextView.setText(
                    if (selectedRecipeIndex >= 0) recipes[selectedRecipeIndex].title else "No linked recipe",
                    false
                )
                editRecipeAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                    selectedRecipeId = if (position == 0) null else recipes[position - 1].id
                }

                saveButton.setOnClickListener {
                    val cleanedIngredients = editIngredientsEditText.text.toString()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    val validation = when {
                        editTitleEditText.text.toString().trim().isBlank() -> "Title is required."
                        cleanedIngredients.isEmpty() -> "Add at least one ingredient."
                        editCaptionEditText.text.toString().trim().length > 200 ->
                            "Caption must be 200 characters or fewer."
                        else -> null
                    }

                    if (validation == null) {
                        editingPostIds.remove(post.id)
                        onUpdatePost(
                            post.id,
                            editTitleEditText.text.toString(),
                            cleanedIngredients,
                            editCaptionEditText.text.toString(),
                            selectedRecipeId
                        )
                    } else {
                        editValidationTextView.text = validation
                    }
                }
                cancelButton.setOnClickListener {
                    editingPostIds.remove(post.id)
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CommunityPostUiModel>() {
        override fun areItemsTheSame(
            oldItem: CommunityPostUiModel,
            newItem: CommunityPostUiModel
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: CommunityPostUiModel,
            newItem: CommunityPostUiModel
        ): Boolean = oldItem == newItem
    }
}
