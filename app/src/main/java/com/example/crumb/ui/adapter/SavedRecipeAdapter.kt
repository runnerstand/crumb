package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemSavedRecipeBinding
import com.example.crumb.ui.screens.recipes.SavedRecipeUiModel

class SavedRecipeAdapter(
    private val onViewRecipe: (SavedRecipeUiModel) -> Unit,
    private val onUnsaveRecipe: (SavedRecipeUiModel) -> Unit,
    private val showUnsaveAction: Boolean = true
) : ListAdapter<SavedRecipeUiModel, SavedRecipeAdapter.SavedRecipeViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedRecipeViewHolder {
        val binding = ItemSavedRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SavedRecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedRecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SavedRecipeViewHolder(
        private val binding: ItemSavedRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: SavedRecipeUiModel) = with(binding) {
            savedRecipeTitleTextView.text = recipe.title
            savedRecipeMetaTextView.text = "${recipe.cookingTimeMinutes} min | ${recipe.ingredients.size} ingredients"
            savedRecipeIngredientsTextView.text = recipe.ingredients.take(4).joinToString(", ")
            viewSavedRecipeButton.setOnClickListener { onViewRecipe(recipe) }
            unsaveRecipeButton.visibility = if (showUnsaveAction) View.VISIBLE else View.GONE
            unsaveRecipeButton.setOnClickListener { onUnsaveRecipe(recipe) }
            root.setOnClickListener { onViewRecipe(recipe) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SavedRecipeUiModel>() {
        override fun areItemsTheSame(
            oldItem: SavedRecipeUiModel,
            newItem: SavedRecipeUiModel
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: SavedRecipeUiModel,
            newItem: SavedRecipeUiModel
        ): Boolean = oldItem == newItem
    }
}
