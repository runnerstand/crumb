package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.CommunityCommentResponse
import com.example.crumb.databinding.ItemCommunityCommentBinding

class CommunityCommentAdapter(
    private val onEditClick: (CommunityCommentResponse) -> Unit,
    private val onDeleteClick: (CommunityCommentResponse) -> Unit
) : RecyclerView.Adapter<CommunityCommentAdapter.ViewHolder>() {
    private val comments = mutableListOf<CommunityCommentResponse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onEditClick,
            onDeleteClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    fun submitComments(items: List<CommunityCommentResponse>) {
        comments.clear()
        comments.addAll(items)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemCommunityCommentBinding,
        private val onEditClick: (CommunityCommentResponse) -> Unit,
        private val onDeleteClick: (CommunityCommentResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: CommunityCommentResponse) {
            binding.commentCreatorName.text = comment.creatorName
            binding.commentCreatedAt.text = comment.createdAt
            binding.commentText.text = comment.commentText
            binding.editCommentButton.setOnClickListener { onEditClick(comment) }
            binding.deleteCommentButton.setOnClickListener { onDeleteClick(comment) }
        }
    }
}
