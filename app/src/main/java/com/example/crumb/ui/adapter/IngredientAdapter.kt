package com.example.crumb.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemIngredientBinding
import com.example.crumb.ui.screens.create.IngredientPickerItem

class IngredientAdapter(
    private val onIngredientClick: (IngredientPickerItem) -> Unit
) : ListAdapter<IngredientPickerItem, IngredientAdapter.IngredientViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemIngredientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IngredientViewHolder(
        private val binding: ItemIngredientBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ingredient: IngredientPickerItem) = with(binding) {
            ingredientNameTextView.text = ingredient.name
            ingredientCategoryTextView.text = ingredient.category.toDisplayLabel()
            root.setOnClickListener { onIngredientClick(ingredient) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<IngredientPickerItem>() {
        override fun areItemsTheSame(
            oldItem: IngredientPickerItem,
            newItem: IngredientPickerItem
        ): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: IngredientPickerItem,
            newItem: IngredientPickerItem
        ): Boolean = oldItem == newItem
    }
}

fun String.toDisplayLabel(): String {
    return split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
        .ifBlank { "Uncategorized" }
}
