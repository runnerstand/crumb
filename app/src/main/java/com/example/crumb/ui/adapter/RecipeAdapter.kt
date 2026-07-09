package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemRecipeBinding
import com.example.crumb.ui.screens.recipes.RecipeUiModel

class RecipeAdapter(
    private val onViewRecipe: (RecipeUiModel) -> Unit,
    private val onEditRecipe: (RecipeUiModel) -> Unit,
    private val onDeleteRecipe: (RecipeUiModel) -> Unit
) : ListAdapter<RecipeUiModel, RecipeAdapter.RecipeViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: RecipeUiModel) = with(binding) {
            recipeTitleTextView.text = recipe.title
            recipeMetaTextView.text = listOfNotNull(
                recipe.cookingTimeMinutes?.let { "$it min" },
                "${recipe.ingredients.size} ingredients"
            ).joinToString(" | ")
            recipeIngredientsTextView.text = recipe.ingredients
                .take(4)
                .joinToString(", ") { it.displayText() }
            editButton.visibility = if (recipe.isOwnedByLocalUser) View.VISIBLE else View.GONE
            deleteButton.visibility = if (recipe.isOwnedByLocalUser) View.VISIBLE else View.GONE

            viewButton.setOnClickListener { onViewRecipe(recipe) }
            editButton.setOnClickListener { onEditRecipe(recipe) }
            deleteButton.setOnClickListener { onDeleteRecipe(recipe) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecipeUiModel>() {
        override fun areItemsTheSame(oldItem: RecipeUiModel, newItem: RecipeUiModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RecipeUiModel, newItem: RecipeUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
