package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.IngredientResponse
import com.example.crumb.databinding.ItemIngredientBinding
import com.example.crumb.databinding.ItemIngredientCategoryHeaderBinding
import java.util.Locale

class IngredientCategoryAdapter(
    private val onIngredientClick: (IngredientResponse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val rows = mutableListOf<IngredientRow>()

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is IngredientRow.Category -> VIEW_TYPE_CATEGORY
            is IngredientRow.Ingredient -> VIEW_TYPE_INGREDIENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> CategoryViewHolder(
                ItemIngredientCategoryHeaderBinding.inflate(inflater, parent, false)
            )
            else -> IngredientViewHolder(
                ItemIngredientBinding.inflate(inflater, parent, false),
                onIngredientClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is IngredientRow.Category -> (holder as CategoryViewHolder).bind(row.name)
            is IngredientRow.Ingredient -> (holder as IngredientViewHolder).bind(row.ingredient)
        }
    }

    override fun getItemCount(): Int = rows.size

    fun submitIngredients(ingredients: List<IngredientResponse>, query: String) {
        val normalizedQuery = query.trim().lowercase(Locale.US)
        val visibleIngredients = ingredients
            .filter { ingredient ->
                normalizedQuery.isEmpty() || ingredient.name.lowercase(Locale.US).contains(normalizedQuery)
            }
            .sortedWith(compareBy<IngredientResponse> { it.category }.thenBy { it.name })
            .groupBy { it.category }

        rows.clear()
        visibleIngredients.forEach { (category, categoryIngredients) ->
            rows += IngredientRow.Category(category)
            rows += categoryIngredients.map { IngredientRow.Ingredient(it) }
        }
        notifyDataSetChanged()
    }

    private class CategoryViewHolder(
        private val binding: ItemIngredientCategoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: String) {
            binding.categoryName.text = category.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
        }
    }

    private class IngredientViewHolder(
        private val binding: ItemIngredientBinding,
        private val onIngredientClick: (IngredientResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ingredient: IngredientResponse) {
            binding.ingredientName.text = ingredient.name
            binding.root.setOnClickListener {
                onIngredientClick(ingredient)
            }
        }
    }

    private sealed interface IngredientRow {
        data class Category(val name: String) : IngredientRow
        data class Ingredient(val ingredient: IngredientResponse) : IngredientRow
    }

    private companion object {
        const val VIEW_TYPE_CATEGORY = 0
        const val VIEW_TYPE_INGREDIENT = 1
    }
}
