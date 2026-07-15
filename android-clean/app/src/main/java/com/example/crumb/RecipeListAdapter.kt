package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.RecipeResponse
import com.example.crumb.databinding.ItemRecipeBinding

class RecipeListAdapter(
    private val onViewClick: (RecipeResponse) -> Unit,
    private val onEditClick: (RecipeResponse) -> Unit,
    private val onDeleteClick: (RecipeResponse) -> Unit
) : RecyclerView.Adapter<RecipeListAdapter.ViewHolder>() {
    private val recipes = mutableListOf<RecipeResponse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onViewClick,
            onEditClick,
            onDeleteClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(recipes[position])
    }

    override fun getItemCount(): Int = recipes.size

    fun submitRecipes(items: List<RecipeResponse>) {
        recipes.clear()
        recipes.addAll(items)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemRecipeBinding,
        private val onViewClick: (RecipeResponse) -> Unit,
        private val onEditClick: (RecipeResponse) -> Unit,
        private val onDeleteClick: (RecipeResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: RecipeResponse) {
            binding.recipeTitle.text = recipe.title
            binding.recipeCookingTime.text = recipe.cookingTimeMinutes?.let {
                "$it minutes"
            } ?: "No cooking time"
            binding.recipeIngredients.text = recipe.ingredients
                .joinToString(", ") { it.ingredientName }
                .ifBlank { "No ingredients" }
            binding.viewRecipeButton.setOnClickListener { onViewClick(recipe) }
            binding.editRecipeButton.setOnClickListener { onEditClick(recipe) }
            binding.deleteRecipeButton.setOnClickListener { onDeleteClick(recipe) }
        }
    }
}
