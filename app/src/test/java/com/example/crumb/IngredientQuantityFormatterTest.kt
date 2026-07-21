package com.example.crumb

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientQuantityFormatterTest {
    @Test
    fun scalesFractionsBeforeConvertingToUsUnits() {
        val result = IngredientQuantityFormatter.format(
            quantity = "1 1/2",
            unit = "kg",
            servingScale = 2.0,
            useMetric = false
        )

        assertEquals("6.61 lb", result)
    }

    @Test
    fun formatsScaledMetricFractionsCleanly() {
        val result = IngredientQuantityFormatter.format(
            quantity = "1/2",
            unit = "cup",
            servingScale = 2.0,
            useMetric = true
        )

        assertEquals("236.59 ml", result)
    }

    @Test
    fun preservesNonNumericQuantities() {
        val result = IngredientQuantityFormatter.format(
            quantity = "to taste",
            unit = "",
            servingScale = 4.0,
            useMetric = false
        )

        assertEquals("to taste", result)
    }

    @Test
    fun preservesUnknownUnitsWhileScalingQuantity() {
        val result = IngredientQuantityFormatter.format(
            quantity = "3/4",
            unit = "bunch",
            servingScale = 2.0,
            useMetric = false
        )

        assertEquals("1 1/2 bunch", result)
    }
}
