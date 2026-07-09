package com.example.crumb.ui.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.databinding.ItemRecipeIngredientEditBinding
import com.example.crumb.ui.screens.recipes.EditableRecipeIngredient

class EditableRecipeIngredientAdapter(
    private val onIngredientsChanged: (List<EditableRecipeIngredient>) -> Unit
) : RecyclerView.Adapter<EditableRecipeIngredientAdapter.IngredientViewHolder>() {
    private val ingredients = mutableListOf<EditableRecipeIngredient>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemRecipeIngredientEditBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position])
    }

    override fun getItemCount(): Int = ingredients.size

    fun submitIngredients(nextIngredients: List<EditableRecipeIngredient>) {
        ingredients.clear()
        ingredients.addAll(nextIngredients)
        notifyDataSetChanged()
    }

    fun addIngredient(ingredientName: String) {
        if (ingredients.any { it.ingredientName == ingredientName }) {
            return
        }

        ingredients.add(EditableRecipeIngredient(ingredientName = ingredientName))
        notifyItemInserted(ingredients.lastIndex)
        notifyChanged()
    }

    fun currentIngredients(): List<EditableRecipeIngredient> = ingredients.toList()

    inner class IngredientViewHolder(
        private val binding: ItemRecipeIngredientEditBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var quantityWatcher: TextWatcher? = null
        private var unitWatcher: TextWatcher? = null

        fun bind(ingredient: EditableRecipeIngredient) = with(binding) {
            quantityWatcher?.let { quantityEditText.removeTextChangedListener(it) }
            unitWatcher?.let { unitEditText.removeTextChangedListener(it) }

            ingredientNameTextView.text = ingredient.ingredientName
            quantityEditText.setText(ingredient.quantity)
            unitEditText.setText(ingredient.unit)

            quantityWatcher = simpleWatcher {
                updateIngredient(bindingAdapterPosition) { current ->
                    current.copy(quantity = it)
                }
            }
            unitWatcher = simpleWatcher {
                updateIngredient(bindingAdapterPosition) { current ->
                    current.copy(unit = it)
                }
            }
            quantityEditText.addTextChangedListener(quantityWatcher)
            unitEditText.addTextChangedListener(unitWatcher)
            removeButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }
                ingredients.removeAt(position)
                notifyItemRemoved(position)
                notifyChanged()
            }
        }
    }

    private fun updateIngredient(
        position: Int,
        update: (EditableRecipeIngredient) -> EditableRecipeIngredient
    ) {
        if (position == RecyclerView.NO_POSITION || position !in ingredients.indices) {
            return
        }

        ingredients[position] = update(ingredients[position])
        notifyChanged()
    }

    private fun notifyChanged() {
        onIngredientsChanged(currentIngredients())
    }
}

private fun simpleWatcher(onTextChanged: (String) -> Unit): TextWatcher {
    return object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
}
