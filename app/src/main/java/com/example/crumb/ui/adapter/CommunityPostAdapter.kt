package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemCommunityPostBinding
import com.example.crumb.ui.screens.home.CommunityPostUiModel

class CommunityPostAdapter(
    private val onOpenComments: (CommunityPostUiModel) -> Unit,
    private val onUpdatePost: (Int, String, List<String>, String) -> Unit,
    private val onDeletePost: (CommunityPostUiModel) -> Unit
) : ListAdapter<CommunityPostUiModel, CommunityPostAdapter.PostViewHolder>(DiffCallback) {
    private val editingPostIds = mutableSetOf<Int>()

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
                            editCaptionEditText.text.toString()
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
