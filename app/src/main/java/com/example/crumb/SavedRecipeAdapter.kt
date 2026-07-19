package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.SavedRecipeResponse
import com.example.crumb.databinding.ItemSavedRecipeBinding

class SavedRecipeAdapter(
    private val onRecipeClick: (SavedRecipeListItem) -> Unit,
    private val onUnsaveClick: (SavedRecipeResponse) -> Unit
) : RecyclerView.Adapter<SavedRecipeAdapter.ViewHolder>() {
    private val items = mutableListOf<SavedRecipeListItem>()
    private val pendingUnsaveIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSavedRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRecipeClick,
            onUnsaveClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], pendingUnsaveIds.contains(items[position].savedRecipe.recipe.id))
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<SavedRecipeListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeRecipe(recipeId: Int) {
        val index = items.indexOfFirst { it.savedRecipe.recipe.id == recipeId }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun setUnsavePending(recipeId: Int, isPending: Boolean) {
        val changed = if (isPending) {
            pendingUnsaveIds.add(recipeId)
        } else {
            pendingUnsaveIds.remove(recipeId)
        }

        if (changed) {
            val index = items.indexOfFirst { it.savedRecipe.recipe.id == recipeId }
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }
    }

    class ViewHolder(
        private val binding: ItemSavedRecipeBinding,
        private val onRecipeClick: (SavedRecipeListItem) -> Unit,
        private val onUnsaveClick: (SavedRecipeResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SavedRecipeListItem, isUnsavePending: Boolean) {
            val recipe = item.savedRecipe.recipe
            val creatorName = recipe.creatorName ?: recipe.userId
            binding.savedRecipeCreatorName.text = creatorName
            binding.savedRecipeTitle.text = recipe.title
            binding.savedRecipeCookingTime.text = recipe.cookingTimeMinutes?.let { "$it minutes" }
                ?: binding.root.context.getString(R.string.no_cooking_time)
            binding.savedRecipeTapHint.text = binding.root.context.getString(R.string.tap_for_recipe_details)
            binding.savedRecipeCard.isClickable = true
            binding.savedRecipeCard.isFocusable = true
            binding.savedRecipeCard.setOnClickListener {
                onRecipeClick(item)
            }
            binding.savedRecipeBookmarkButton.isEnabled = !isUnsavePending
            binding.savedRecipeBookmarkButton.alpha = if (isUnsavePending) 0.5f else 1f
            binding.savedRecipeBookmarkButton.setOnClickListener {
                onUnsaveClick(item.savedRecipe)
            }
        }
    }

    data class SavedRecipeListItem(
        val savedRecipe: SavedRecipeResponse,
        val postId: Int?
    )

    fun currentItems(): List<SavedRecipeListItem> = items.toList()
}
