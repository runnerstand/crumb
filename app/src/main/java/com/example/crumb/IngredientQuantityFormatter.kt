package com.example.crumb

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object IngredientQuantityFormatter {
    private const val OUNCE_IN_GRAMS = 28.3495
    private const val POUND_IN_GRAMS = 453.592
    private const val US_FL_OUNCE_IN_ML = 29.5735
    private const val US_CUP_IN_ML = 236.588
    private const val LITER_IN_ML = 1000.0
    private const val KILOGRAM_IN_GRAMS = 1000.0
    private const val QUANTITY_EPSILON = 0.0001
    private const val FRACTION_EPSILON = 0.02
    private val SIMPLE_FRACTION_DENOMINATORS = listOf(2, 3, 4, 8)

    fun format(
        quantity: String?,
        unit: String?,
        servingScale: Double,
        useMetric: Boolean
    ): String {
        val rawQuantity = quantity?.trim().orEmpty()
        val rawUnit = unit?.trim().orEmpty()
        if (rawQuantity.isBlank()) {
            return rawUnit
        }

        val parsedQuantity = parseQuantity(rawQuantity)
        if (parsedQuantity == null) {
            return listOf(rawQuantity, rawUnit).filter { it.isNotBlank() }.joinToString(" ")
        }

        val scaledQuantity = parsedQuantity * servingScale
        val converted = convertQuantity(scaledQuantity, rawUnit, useMetric)
        val formattedQuantity = if (converted.wasConverted) {
            formatDecimal(converted.quantity)
        } else {
            formatFractionFriendly(converted.quantity)
        }

        return listOf(formattedQuantity, converted.unit).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun parseQuantity(quantity: String): Double? {
        val parts = quantity.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when (parts.size) {
            1 -> parseQuantityPart(parts[0])
            2 -> {
                val whole = parts[0].toDoubleOrNull() ?: return null
                val fraction = parseFraction(parts[1]) ?: return null
                whole + fraction
            }
            else -> null
        }
    }

    private fun parseQuantityPart(value: String): Double? {
        return parseFraction(value) ?: value.toDoubleOrNull()?.takeIf { it >= 0.0 }
    }

    private fun parseFraction(value: String): Double? {
        val pieces = value.split("/")
        if (pieces.size != 2) return null

        val numerator = pieces[0].toDoubleOrNull() ?: return null
        val denominator = pieces[1].toDoubleOrNull() ?: return null
        if (denominator == 0.0 || numerator < 0.0 || denominator < 0.0) return null

        return numerator / denominator
    }

    private fun convertQuantity(quantity: Double, unit: String, useMetric: Boolean): ConvertedQuantity {
        return when (normalizeUnit(unit)) {
            NormalizedUnit.GRAM -> if (useMetric) same(quantity, "g") else converted(quantity / OUNCE_IN_GRAMS, "oz")
            NormalizedUnit.KILOGRAM -> if (useMetric) same(quantity, "kg") else converted(quantity * KILOGRAM_IN_GRAMS / POUND_IN_GRAMS, "lb")
            NormalizedUnit.OUNCE -> if (useMetric) converted(quantity * OUNCE_IN_GRAMS, "g") else same(quantity, "oz")
            NormalizedUnit.POUND -> if (useMetric) converted(quantity * POUND_IN_GRAMS / KILOGRAM_IN_GRAMS, "kg") else same(quantity, "lb")
            NormalizedUnit.MILLILITER -> if (useMetric) same(quantity, "ml") else converted(quantity / US_FL_OUNCE_IN_ML, "US fl oz")
            NormalizedUnit.LITER -> if (useMetric) same(quantity, "l") else converted(quantity * LITER_IN_ML / US_CUP_IN_ML, "US cups")
            NormalizedUnit.CUP -> if (useMetric) converted(quantity * US_CUP_IN_ML, "ml") else same(quantity, normalizedCupUnit(quantity))
            NormalizedUnit.FLUID_OUNCE -> if (useMetric) converted(quantity * US_FL_OUNCE_IN_ML, "ml") else same(quantity, "US fl oz")
            NormalizedUnit.TEASPOON -> same(quantity, unit)
            NormalizedUnit.TABLESPOON -> same(quantity, unit)
            NormalizedUnit.COUNT,
            NormalizedUnit.UNKNOWN -> same(quantity, unit)
        }
    }

    private fun normalizeUnit(unit: String): NormalizedUnit {
        return when (unit.trim().lowercase(Locale.US)) {
            "g", "gram", "grams" -> NormalizedUnit.GRAM
            "kg", "kilogram", "kilograms" -> NormalizedUnit.KILOGRAM
            "oz", "ounce", "ounces" -> NormalizedUnit.OUNCE
            "lb", "lbs", "pound", "pounds" -> NormalizedUnit.POUND
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> NormalizedUnit.MILLILITER
            "l", "liter", "liters", "litre", "litres" -> NormalizedUnit.LITER
            "cup", "cups" -> NormalizedUnit.CUP
            "fl oz", "fluid ounce", "fluid ounces" -> NormalizedUnit.FLUID_OUNCE
            "tsp" -> NormalizedUnit.TEASPOON
            "tbsp" -> NormalizedUnit.TABLESPOON
            "piece", "pieces", "clove", "cloves", "egg", "eggs", "slice", "slices" -> NormalizedUnit.COUNT
            else -> NormalizedUnit.UNKNOWN
        }
    }

    private fun same(quantity: Double, unit: String) = ConvertedQuantity(quantity, unit, wasConverted = false)

    private fun converted(quantity: Double, unit: String) = ConvertedQuantity(quantity, unit, wasConverted = true)

    private fun normalizedCupUnit(quantity: Double): String {
        return if (abs(quantity - 1.0) < QUANTITY_EPSILON) "cup" else "cups"
    }

    private fun formatFractionFriendly(quantity: Double): String {
        if (abs(quantity) < QUANTITY_EPSILON) return "0"

        val whole = floor(quantity + QUANTITY_EPSILON).toInt()
        val fraction = quantity - whole
        if (abs(fraction) < QUANTITY_EPSILON) return whole.toString()

        val fractionText = formatSimpleFraction(fraction)
        if (fractionText != null) {
            return if (whole == 0) fractionText else "$whole $fractionText"
        }

        return formatDecimal(quantity)
    }

    private fun formatSimpleFraction(value: Double): String? {
        SIMPLE_FRACTION_DENOMINATORS.forEach { denominator ->
            val numerator = (value * denominator).roundToInt()
            if (numerator > 0 && numerator < denominator) {
                val approximation = numerator.toDouble() / denominator.toDouble()
                if (abs(value - approximation) < FRACTION_EPSILON) {
                    return "$numerator/$denominator"
                }
            }
        }

        return null
    }

    private fun formatDecimal(quantity: Double): String {
        return String.format(Locale.US, "%.2f", quantity)
            .trimEnd('0')
            .trimEnd('.')
    }

    private data class ConvertedQuantity(
        val quantity: Double,
        val unit: String,
        val wasConverted: Boolean
    )

    private enum class NormalizedUnit {
        GRAM,
        KILOGRAM,
        OUNCE,
        POUND,
        MILLILITER,
        LITER,
        CUP,
        FLUID_OUNCE,
        TEASPOON,
        TABLESPOON,
        COUNT,
        UNKNOWN
    }
}
