package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemCommunityPostBinding

class SearchRecipeAdapter(
    private val onRecipeClick: (SearchRecipeCardItem) -> Unit
) : RecyclerView.Adapter<SearchRecipeAdapter.ViewHolder>() {
    private val items = mutableListOf<SearchRecipeCardItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRecipeClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<SearchRecipeCardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemCommunityPostBinding,
        private val onRecipeClick: (SearchRecipeCardItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchRecipeCardItem) {
            binding.postCreatorName.text = item.creatorName
            binding.postTitle.text = item.title
            binding.postCookingTime.text = item.cookingTimeText
            binding.postIngredients.visibility = android.view.View.GONE
            binding.postCaption.visibility = android.view.View.GONE
            binding.postRating.visibility = android.view.View.GONE
            binding.postTapHint.visibility = android.view.View.VISIBLE
            binding.postActionsRow.visibility = android.view.View.GONE
            binding.postRecipeImage.visibility = android.view.View.VISIBLE
            binding.communityPostCard.isClickable = true
            binding.communityPostCard.isFocusable = true
            binding.communityPostCard.setOnClickListener {
                onRecipeClick(item)
            }
        }
    }
}

data class SearchRecipeCardItem(
    val recipeId: Int,
    val postId: Int?,
    val title: String,
    val creatorName: String,
    val cookingTimeText: String,
    val ingredients: List<String>,
    val sortKey: Long
)
