package com.example.crumb

import android.content.Context
import java.util.Locale

object ProfilePreferenceStore {
    const val VALUE_VEGETARIAN = "vegetarian"
    const val VALUE_PEANUTS = "peanuts"
    const val VALUE_TREE_NUTS = "tree nuts"
    const val VALUE_SHELLFISH = "shellfish"
    const val VALUE_MILK = "milk"
    const val VALUE_EGGS = "egg"
    const val VALUE_SOY = "soy sauce"

    private const val PREFERENCES_NAME = "crumb_profile_preferences"
    private const val KEY_DIETARY_PREFERENCES = "dietary_preferences"
    private const val KEY_AVOID_INGREDIENTS = "avoid_ingredients"

    fun isVegetarianEnabled(context: Context): Boolean {
        return context.profilePreferences()
            .getStringSet(KEY_DIETARY_PREFERENCES, emptySet())
            .orEmpty()
            .contains(VALUE_VEGETARIAN)
    }

    fun loadDietaryPreferences(context: Context): Set<String> {
        val savedValues = context.profilePreferences()
            .getStringSet(KEY_DIETARY_PREFERENCES, emptySet())
            .orEmpty()
        return setOf(VALUE_VEGETARIAN).filterTo(mutableSetOf()) { savedValues.contains(it) }
    }

    fun saveDietaryPreferences(context: Context, values: Set<String>) {
        context.profilePreferences()
            .edit()
            .putStringSet(KEY_DIETARY_PREFERENCES, values.intersect(setOf(VALUE_VEGETARIAN)))
            .apply()
    }

    fun loadAvoidIngredients(context: Context): Set<String> {
        return context.profilePreferences()
            .getStringSet(KEY_AVOID_INGREDIENTS, emptySet())
            .orEmpty()
            .map { it.canonicalAvoidValue() }
            .toSet()
    }

    fun saveAvoidIngredients(context: Context, values: Set<String>) {
        context.profilePreferences()
            .edit()
            .putStringSet(KEY_AVOID_INGREDIENTS, values.map { it.canonicalAvoidValue() }.toSet())
            .apply()
    }

    fun savePreferences(
        context: Context,
        dietaryPreferences: Set<String>,
        avoidIngredients: Set<String>
    ) {
        context.profilePreferences()
            .edit()
            .putStringSet(KEY_DIETARY_PREFERENCES, dietaryPreferences.intersect(setOf(VALUE_VEGETARIAN)))
            .putStringSet(KEY_AVOID_INGREDIENTS, avoidIngredients.map { it.canonicalAvoidValue() }.toSet())
            .apply()
    }

    private fun Context.profilePreferences() =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun String.canonicalAvoidValue(): String {
        return when (trim().lowercase(Locale.US)) {
            "tree_nuts" -> VALUE_TREE_NUTS
            "eggs" -> VALUE_EGGS
            "soy" -> VALUE_SOY
            else -> trim().lowercase(Locale.US)
        }
    }
}

data class RecipePreferenceConflict(
    val ingredientName: String,
    val reason: ConflictReason
)

enum class ConflictReason {
    VEGETARIAN,
    AVOID
}

object RecipePreferenceConflictChecker {
    private val nonVegetarianIngredients = setOf(
        "minced beef",
        "minced lamb",
        "minced pork"
    )

    fun findConflicts(
        context: Context,
        selectedIngredientNames: Collection<String>
    ): List<RecipePreferenceConflict> {
        val vegetarianEnabled = ProfilePreferenceStore.isVegetarianEnabled(context)
        val avoidedIngredients = ProfilePreferenceStore.loadAvoidIngredients(context)
        val conflicts = linkedMapOf<String, RecipePreferenceConflict>()

        selectedIngredientNames.forEach { ingredientName ->
            val canonicalName = ingredientName.trim().lowercase(Locale.US)
            if (canonicalName.isBlank()) {
                return@forEach
            }

            if (vegetarianEnabled && canonicalName in nonVegetarianIngredients) {
                conflicts["vegetarian:$canonicalName"] = RecipePreferenceConflict(
                    ingredientName = ingredientName,
                    reason = ConflictReason.VEGETARIAN
                )
            }

            if (canonicalName in avoidedIngredients) {
                conflicts["avoid:$canonicalName"] = RecipePreferenceConflict(
                    ingredientName = ingredientName,
                    reason = ConflictReason.AVOID
                )
            }
        }

        return conflicts.values.toList()
    }
}
