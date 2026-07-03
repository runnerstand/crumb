import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DATA_DIR = Path(__file__).resolve().parents[1] / "data"
INGREDIENTS_FILE = DATA_DIR / "ingredients.json"
DEFAULT_CATEGORY = "uncategorized"


@dataclass(frozen=True)
class Ingredient:
    name: str
    aliases: tuple[str, ...]
    category: str
    warning_tags: tuple[str, ...]
    needs_review: bool


def normalize_ingredient(value: str) -> str:
    return " ".join(value.strip().lower().split())


def load_ingredients(path: Path = INGREDIENTS_FILE) -> list[Ingredient]:
    with path.open(encoding="utf-8") as ingredient_file:
        raw_ingredients = json.load(ingredient_file)

    if not isinstance(raw_ingredients, list):
        raise ValueError("Ingredient data must be a list.")

    ingredients: list[Ingredient] = []

    for raw_ingredient in raw_ingredients:
        if not isinstance(raw_ingredient, dict):
            continue

        name = normalize_ingredient(str(raw_ingredient.get("name", "")))

        if not name:
            continue

        aliases = tuple(
            alias
            for alias in (
                normalize_ingredient(str(alias))
                for alias in raw_ingredient.get("aliases", [])
                if isinstance(alias, str)
            )
            if alias and alias != name
        )
        warning_tags = tuple(
            tag
            for tag in (
                normalize_ingredient(str(tag))
                for tag in raw_ingredient.get("warning_tags", [])
                if isinstance(tag, str)
            )
            if tag
        )
        category = normalize_ingredient(str(raw_ingredient.get("category", "")))

        ingredients.append(
            Ingredient(
                name=name,
                aliases=aliases,
                category=category or DEFAULT_CATEGORY,
                warning_tags=warning_tags,
                needs_review=bool(raw_ingredient.get("needs_review", False)),
            )
        )

    return ingredients


def build_alias_lookup(ingredients: list[Ingredient]) -> dict[str, str]:
    alias_lookup: dict[str, str] = {}

    for ingredient in ingredients:
        alias_lookup[ingredient.name] = ingredient.name

        for alias in ingredient.aliases:
            alias_lookup[alias] = ingredient.name

    return alias_lookup


def canonicalize_ingredient(
    value: str,
    ingredients: list[Ingredient] | None = None,
) -> str:
    normalized_value = normalize_ingredient(value)

    if not normalized_value:
        return ""

    alias_lookup = build_alias_lookup(ingredients or load_ingredients())

    return alias_lookup.get(normalized_value, normalized_value)


def build_ingredient_names(ingredients: list[Ingredient] | None = None) -> list[str]:
    return sorted({ingredient.name for ingredient in ingredients or load_ingredients()})


def build_ingredient_categories(
    ingredients: list[Ingredient] | None = None,
) -> list[dict[str, Any]]:
    return [
        {
            "name": ingredient.name,
            "category": ingredient.category,
            "tags": list(ingredient.warning_tags),
        }
        for ingredient in sorted(
            ingredients or load_ingredients(),
            key=lambda ingredient: ingredient.name,
        )
    ]


def build_warning_tags(
    values: list[str],
    ingredients: list[Ingredient] | None = None,
) -> list[str]:
    catalogue = ingredients or load_ingredients()
    alias_lookup = build_alias_lookup(catalogue)
    warning_tags_by_name = {
        ingredient.name: ingredient.warning_tags for ingredient in catalogue
    }
    warning_tags = {
        tag
        for value in values
        for tag in warning_tags_by_name.get(
            alias_lookup.get(normalize_ingredient(value), normalize_ingredient(value)),
            (),
        )
    }

    return sorted(warning_tags)


def ingredient_to_json(ingredient: Ingredient) -> dict[str, Any]:
    return {
        "name": ingredient.name,
        "aliases": list(ingredient.aliases),
        "category": ingredient.category,
        "warning_tags": list(ingredient.warning_tags),
        "needs_review": ingredient.needs_review,
    }
