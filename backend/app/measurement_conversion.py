from __future__ import annotations

from dataclasses import dataclass
from typing import Literal


MeasurementSystem = Literal["metric", "us"]

GRAMS_PER_OUNCE = 28.3495
KILOGRAMS_PER_POUND = 0.453592
MILLILITRES_PER_CUP = 240
MILLILITRES_PER_TABLESPOON = 15
MILLILITRES_PER_TEASPOON = 5
MILLILITRES_PER_LITRE = 1000

UNIT_ALIASES = {
    "g": "grams",
    "gram": "grams",
    "grams": "grams",
    "kg": "kilograms",
    "kilogram": "kilograms",
    "kilograms": "kilograms",
    "oz": "ounces",
    "ounce": "ounces",
    "ounces": "ounces",
    "lb": "pounds",
    "lbs": "pounds",
    "pound": "pounds",
    "pounds": "pounds",
    "ml": "millilitres",
    "millilitre": "millilitres",
    "millilitres": "millilitres",
    "milliliter": "millilitres",
    "milliliters": "millilitres",
    "l": "litres",
    "litre": "litres",
    "litres": "litres",
    "liter": "litres",
    "liters": "litres",
    "cup": "cups",
    "cups": "cups",
    "tbsp": "tablespoons",
    "tablespoon": "tablespoons",
    "tablespoons": "tablespoons",
    "tsp": "teaspoons",
    "teaspoon": "teaspoons",
    "teaspoons": "teaspoons",
    "c": "celsius",
    "celsius": "celsius",
    "f": "fahrenheit",
    "fahrenheit": "fahrenheit",
}

VAGUE_PREFIXES = ("to taste", "a pinch", "pinch")


@dataclass(frozen=True)
class ConvertedMeasurement:
    original_text: str
    text: str
    original_quantity: float | None = None
    original_unit: str | None = None
    converted_quantity: float | None = None
    converted_unit: str | None = None


def convert_measurement_text(
    value: str,
    measurement_system: MeasurementSystem,
) -> ConvertedMeasurement:
    cleaned_value = " ".join(value.strip().split())

    if not cleaned_value or cleaned_value.lower().startswith(VAGUE_PREFIXES):
        return ConvertedMeasurement(original_text=value, text=value)

    parts = cleaned_value.split(" ", 2)

    if len(parts) < 2:
        return ConvertedMeasurement(original_text=value, text=value)

    quantity = parse_quantity(parts[0])
    unit = UNIT_ALIASES.get(parts[1].lower().rstrip("."))

    if quantity is None or unit is None:
        return ConvertedMeasurement(original_text=value, text=value)

    converted = convert_quantity(quantity, unit, measurement_system)

    if converted is None:
        return ConvertedMeasurement(
            original_text=value,
            text=value,
            original_quantity=quantity,
            original_unit=unit,
        )

    converted_quantity, converted_unit = converted
    suffix = f" {parts[2]}" if len(parts) == 3 else ""
    converted_text = f"{format_quantity(converted_quantity)} {format_unit(converted_quantity, converted_unit)}{suffix}"

    return ConvertedMeasurement(
        original_text=value,
        text=converted_text,
        original_quantity=quantity,
        original_unit=unit,
        converted_quantity=converted_quantity,
        converted_unit=converted_unit,
    )


def parse_quantity(value: str) -> float | None:
    if "/" in value:
        numerator, denominator = value.split("/", 1)

        try:
            denominator_value = float(denominator)

            if denominator_value == 0:
                return None

            return float(numerator) / denominator_value
        except ValueError:
            return None

    try:
        return float(value)
    except ValueError:
        return None


def convert_quantity(
    quantity: float,
    unit: str,
    measurement_system: MeasurementSystem,
) -> tuple[float, str] | None:
    if measurement_system == "us":
        if unit == "grams":
            return quantity / GRAMS_PER_OUNCE, "ounces"
        if unit == "kilograms":
            return quantity / KILOGRAMS_PER_POUND, "pounds"
        if unit == "millilitres":
            return convert_millilitres_to_us(quantity)
        if unit == "litres":
            return quantity * MILLILITRES_PER_LITRE / MILLILITRES_PER_CUP, "cups"
        if unit == "celsius":
            return quantity * 9 / 5 + 32, "fahrenheit"
        return None

    if unit == "ounces":
        return quantity * GRAMS_PER_OUNCE, "grams"
    if unit == "pounds":
        return quantity * KILOGRAMS_PER_POUND, "kilograms"
    if unit == "cups":
        return quantity * MILLILITRES_PER_CUP, "millilitres"
    if unit == "tablespoons":
        return quantity * MILLILITRES_PER_TABLESPOON, "millilitres"
    if unit == "teaspoons":
        return quantity * MILLILITRES_PER_TEASPOON, "millilitres"
    if unit == "fahrenheit":
        return (quantity - 32) * 5 / 9, "celsius"

    return None


def convert_millilitres_to_us(quantity: float) -> tuple[float, str]:
    if quantity <= MILLILITRES_PER_TEASPOON * 2:
        return quantity / MILLILITRES_PER_TEASPOON, "teaspoons"

    if quantity <= MILLILITRES_PER_TABLESPOON * 4:
        return quantity / MILLILITRES_PER_TABLESPOON, "tablespoons"

    return quantity / MILLILITRES_PER_CUP, "cups"


def format_quantity(value: float) -> str:
    rounded_value = round(value, 1)

    if rounded_value.is_integer():
        return str(int(rounded_value))

    return f"{rounded_value:.1f}"


def format_unit(quantity: float, unit: str) -> str:
    if round(quantity, 1) == 1:
        singular_units = {
            "grams": "gram",
            "kilograms": "kilogram",
            "ounces": "ounce",
            "pounds": "pound",
            "millilitres": "millilitre",
            "litres": "litre",
            "cups": "cup",
            "tablespoons": "tablespoon",
            "teaspoons": "teaspoon",
        }

        return singular_units.get(unit, unit)

    return unit
