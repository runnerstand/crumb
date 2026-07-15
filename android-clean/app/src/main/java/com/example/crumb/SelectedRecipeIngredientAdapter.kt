package com.example.crumb

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemSelectedRecipeIngredientBinding

class SelectedRecipeIngredientAdapter(
    private val onRemoveClick: (SelectedRecipeIngredient) -> Unit
) : RecyclerView.Adapter<SelectedRecipeIngredientAdapter.ViewHolder>() {
    private val items = mutableListOf<SelectedRecipeIngredient>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSelectedRecipeIngredientBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onRemoveClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitIngredients(ingredients: List<SelectedRecipeIngredient>) {
        items.clear()
        items.addAll(ingredients)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemSelectedRecipeIngredientBinding,
        private val onRemoveClick: (SelectedRecipeIngredient) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ingredient: SelectedRecipeIngredient) {
            binding.selectedIngredientName.text = ingredient.name
            binding.quantityEditText.setText(ingredient.quantity)
            binding.unitEditText.setText(ingredient.unit)
            binding.quantityEditText.doAfterTextChanged {
                ingredient.quantity = it?.toString().orEmpty()
            }
            binding.unitEditText.doAfterTextChanged {
                ingredient.unit = it?.toString().orEmpty()
            }
            binding.removeIngredientButton.setOnClickListener {
                onRemoveClick(ingredient)
            }
        }
    }
}
