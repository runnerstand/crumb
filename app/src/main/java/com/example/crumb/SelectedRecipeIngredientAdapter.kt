package com.example.crumb

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemSelectedRecipeIngredientBinding

class SelectedRecipeIngredientAdapter(
    private val onRemoveClick: (SelectedRecipeIngredient) -> Unit,
    private val onIngredientChanged: () -> Unit
) : RecyclerView.Adapter<SelectedRecipeIngredientAdapter.ViewHolder>() {
    private val items = mutableListOf<SelectedRecipeIngredient>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSelectedRecipeIngredientBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onRemoveClick,
            onIngredientChanged
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
        private val onRemoveClick: (SelectedRecipeIngredient) -> Unit,
        private val onIngredientChanged: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var quantityWatcher: TextWatcher? = null
        private var unitWatcher: TextWatcher? = null

        fun bind(ingredient: SelectedRecipeIngredient) {
            quantityWatcher?.let { binding.quantityEditText.removeTextChangedListener(it) }
            unitWatcher?.let { binding.unitEditText.removeTextChangedListener(it) }

            binding.selectedIngredientName.text = ingredient.name
            binding.quantityEditText.setText(ingredient.quantity)
            binding.unitEditText.setText(ingredient.unit)
            quantityWatcher = SimpleTextWatcher {
                ingredient.quantity = it
                onIngredientChanged()
            }
            unitWatcher = SimpleTextWatcher {
                ingredient.unit = it
                onIngredientChanged()
            }
            binding.quantityEditText.addTextChangedListener(quantityWatcher)
            binding.unitEditText.addTextChangedListener(unitWatcher)
            binding.removeIngredientButton.setOnClickListener {
                onRemoveClick(ingredient)
            }
        }
    }

    private class SimpleTextWatcher(
        private val onTextChanged: (String) -> Unit
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged(s?.toString().orEmpty())
        }

        override fun afterTextChanged(s: Editable?) = Unit
    }
}
