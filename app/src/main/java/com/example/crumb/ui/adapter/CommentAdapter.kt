package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemCommentBinding
import com.example.crumb.ui.screens.comments.CommunityCommentUiModel

class CommentAdapter(
    private val onUpdateComment: (Int, String) -> Unit,
    private val onDeleteComment: (CommunityCommentUiModel) -> Unit
) : ListAdapter<CommunityCommentUiModel, CommentAdapter.CommentViewHolder>(DiffCallback) {
    private val editingCommentIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position), editingCommentIds.contains(getItem(position).id))
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: CommunityCommentUiModel, isEditing: Boolean) = with(binding) {
            commentDisplayContainer.visibility = if (isEditing) View.GONE else View.VISIBLE
            commentEditContainer.visibility = if (isEditing) View.VISIBLE else View.GONE

            commentCreatorTextView.text = comment.creatorName
            commentTextView.text = comment.commentText
            commentDatesTextView.text = "Created ${comment.createdAt} | Updated ${comment.updatedAt}"
            commentActionsContainer.visibility =
                if (comment.isOwnedByLocalUser) View.VISIBLE else View.GONE
            commentEditButton.setOnClickListener {
                editingCommentIds.add(comment.id)
                notifyItemChanged(bindingAdapterPosition)
            }
            commentDeleteButton.setOnClickListener { onDeleteComment(comment) }

            if (isEditing) {
                commentEditText.setText(comment.commentText)
                commentValidationTextView.text = ""
                commentSaveButton.setOnClickListener {
                    val text = commentEditText.text.toString()
                    val validation = when {
                        text.trim().isBlank() -> "Comment is required."
                        text.trim().length > 500 -> "Comment must be 500 characters or fewer."
                        else -> null
                    }
                    if (validation == null) {
                        editingCommentIds.remove(comment.id)
                        onUpdateComment(comment.id, text)
                    } else {
                        commentValidationTextView.text = validation
                    }
                }
                commentCancelButton.setOnClickListener {
                    editingCommentIds.remove(comment.id)
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CommunityCommentUiModel>() {
        override fun areItemsTheSame(
            oldItem: CommunityCommentUiModel,
            newItem: CommunityCommentUiModel
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: CommunityCommentUiModel,
            newItem: CommunityCommentUiModel
        ): Boolean = oldItem == newItem
    }
}
