package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.databinding.ItemCommunityPostBinding

class CommunityPostAdapter(
    private val onCommentsClick: (CommunityPostResponse) -> Unit,
    private val onEditClick: (CommunityPostResponse) -> Unit,
    private val onDeleteClick: (CommunityPostResponse) -> Unit
) : RecyclerView.Adapter<CommunityPostAdapter.ViewHolder>() {
    private val posts = mutableListOf<CommunityPostResponse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onCommentsClick,
            onEditClick,
            onDeleteClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    fun submitPosts(items: List<CommunityPostResponse>) {
        posts.clear()
        posts.addAll(items)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemCommunityPostBinding,
        private val onCommentsClick: (CommunityPostResponse) -> Unit,
        private val onEditClick: (CommunityPostResponse) -> Unit,
        private val onDeleteClick: (CommunityPostResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: CommunityPostResponse) {
            binding.postCreatorName.text = post.creatorName
            binding.postTitle.text = post.title
            binding.postIngredients.text = post.ingredientsJson
                .joinToString(", ")
                .ifBlank { "No ingredients" }
            binding.postCaption.text = post.caption
            binding.commentsButton.setOnClickListener { onCommentsClick(post) }
            binding.editPostButton.setOnClickListener { onEditClick(post) }
            binding.deletePostButton.setOnClickListener { onDeleteClick(post) }
        }
    }
}
