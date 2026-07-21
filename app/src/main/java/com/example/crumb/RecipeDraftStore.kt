package com.example.crumb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecipeDraftIngredient(
    val name: String,
    val quantity: String,
    val unit: String
)

data class RecipeDraft(
    val title: String,
    val cookingTime: String,
    val servings: String,
    val instructions: String,
    val ingredients: List<RecipeDraftIngredient>
)

class RecipeDraftStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): RecipeDraft? {
        val rawDraft = preferences.getString(KEY_DRAFT_JSON, null) ?: return null

        return try {
            val draft = parseDraft(JSONObject(rawDraft))
            if (!draft.hasMeaningfulContent()) {
                clear()
                null
            } else {
                draft
            }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(draft: RecipeDraft) {
        preferences.edit()
            .putString(KEY_DRAFT_JSON, draft.toJson().toString())
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_DRAFT_JSON)
            .apply()
    }

    fun hasDraft(): Boolean {
        return preferences.contains(KEY_DRAFT_JSON)
    }

    private fun parseDraft(json: JSONObject): RecipeDraft {
        val ingredientsJson = json.optJSONArray(KEY_INGREDIENTS).orEmpty()
        val ingredients = buildList {
            for (index in 0 until ingredientsJson.length()) {
                val ingredientJson = ingredientsJson.optJSONObject(index) ?: continue
                val name = ingredientJson.optString(KEY_NAME).trim()
                if (name.isBlank()) {
                    continue
                }
                add(
                    RecipeDraftIngredient(
                        name = name,
                        quantity = ingredientJson.optString(KEY_QUANTITY),
                        unit = ingredientJson.optString(KEY_UNIT)
                    )
                )
            }
        }

        return RecipeDraft(
            title = json.optString(KEY_TITLE),
            cookingTime = json.optString(KEY_COOKING_TIME),
            servings = json.optString(KEY_SERVINGS, DEFAULT_SERVINGS),
            instructions = json.optString(KEY_INSTRUCTIONS),
            ingredients = ingredients
        )
    }

    private fun RecipeDraft.toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_TITLE, title)
            put(KEY_COOKING_TIME, cookingTime)
            put(KEY_SERVINGS, servings)
            put(KEY_INSTRUCTIONS, instructions)
            put(KEY_INGREDIENTS, JSONArray().apply {
                ingredients.forEach { ingredient ->
                    put(
                        JSONObject().apply {
                            put(KEY_NAME, ingredient.name)
                            put(KEY_QUANTITY, ingredient.quantity)
                            put(KEY_UNIT, ingredient.unit)
                        }
                    )
                }
            })
        }
    }

    private fun RecipeDraft.hasMeaningfulContent(): Boolean {
        return title.isNotBlank() ||
            cookingTime.isNotBlank() ||
            servings.isNotBlank() && servings != DEFAULT_SERVINGS ||
            instructions.isNotBlank() ||
            ingredients.isNotEmpty()
    }

    private fun JSONArray?.orEmpty(): JSONArray {
        return this ?: JSONArray()
    }

    companion object {
        private const val PREF_NAME = "crumb_recipe_draft"
        private const val KEY_DRAFT_JSON = "draft_json"
        private const val KEY_TITLE = "title"
        private const val KEY_COOKING_TIME = "cooking_time"
        private const val KEY_SERVINGS = "servings"
        private const val KEY_INSTRUCTIONS = "instructions"
        private const val KEY_INGREDIENTS = "ingredients"
        private const val KEY_NAME = "name"
        private const val KEY_QUANTITY = "quantity"
        private const val KEY_UNIT = "unit"
        private const val DEFAULT_SERVINGS = "2"
    }
}
