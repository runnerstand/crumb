package com.example.crumb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.databinding.ItemCommunityPostBinding

class CommunityPostAdapter(
    private val onRecipePostClick: (postId: Int, recipeId: Int) -> Unit,
    private val onLegacyCommentsClick: (CommunityPostResponse) -> Unit,
    private val onLegacyEditClick: (CommunityPostResponse) -> Unit,
    private val onLegacyDeleteClick: (CommunityPostResponse) -> Unit
) : RecyclerView.Adapter<CommunityPostAdapter.ViewHolder>() {
    private val posts = mutableListOf<CommunityPostResponse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRecipePostClick,
            onLegacyCommentsClick,
            onLegacyEditClick,
            onLegacyDeleteClick
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
        private val onRecipePostClick: (postId: Int, recipeId: Int) -> Unit,
        private val onLegacyCommentsClick: (CommunityPostResponse) -> Unit,
        private val onLegacyEditClick: (CommunityPostResponse) -> Unit,
        private val onLegacyDeleteClick: (CommunityPostResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: CommunityPostResponse) {
            val linkedRecipeId = post.recipeId ?: post.recipe?.id
            val isLinkedRecipePost = linkedRecipeId != null

            binding.postCreatorName.text = post.creatorName
            binding.postTitle.text = post.title
            binding.postIngredients.text = if (isLinkedRecipePost) {
                ""
            } else {
                post.ingredientsJson
                    .joinToString(", ")
                    .ifBlank { "No ingredients" }
            }
            binding.postIngredients.visibility = if (isLinkedRecipePost) View.GONE else View.VISIBLE
            binding.postCookingTime.text = post.recipe?.cookingTimeMinutes
                ?.let { "$it minutes" }
                .orEmpty()
            binding.postCookingTime.visibility =
                if (post.recipe?.cookingTimeMinutes == null) View.GONE else View.VISIBLE
            binding.postCaption.text = post.caption
            binding.postCaption.visibility = if (isLinkedRecipePost) {
                View.GONE
            } else if (post.caption.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.postRecipeImage.visibility = if (isLinkedRecipePost) View.VISIBLE else View.GONE
            binding.postRating.visibility = if (isLinkedRecipePost) View.VISIBLE else View.GONE
            binding.postTapHint.visibility = if (isLinkedRecipePost) View.VISIBLE else View.GONE
            binding.postActionsRow.visibility = if (isLinkedRecipePost) View.GONE else View.VISIBLE
            binding.editPostButton.visibility = if (post.recipe == null) View.VISIBLE else View.GONE
            binding.communityPostCard.isClickable = isLinkedRecipePost
            binding.communityPostCard.isFocusable = isLinkedRecipePost
            binding.communityPostCard.setOnClickListener(
                if (linkedRecipeId == null) {
                    null
                } else {
                    View.OnClickListener { onRecipePostClick(post.id, linkedRecipeId) }
                }
            )
            binding.viewRecipeButton.setOnClickListener(null)
            if (isLinkedRecipePost) {
                binding.commentsButton.setOnClickListener(null)
                binding.editPostButton.setOnClickListener(null)
                binding.deletePostButton.setOnClickListener(null)
            } else {
                binding.commentsButton.setOnClickListener { onLegacyCommentsClick(post) }
                binding.editPostButton.setOnClickListener { onLegacyEditClick(post) }
                binding.deletePostButton.setOnClickListener { onLegacyDeleteClick(post) }
            }
        }
    }
}
