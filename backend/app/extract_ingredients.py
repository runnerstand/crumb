import argparse
import json
from pathlib import Path
from typing import Any

from app.ingredient_catalog import DEFAULT_CATEGORY
from app.ingredient_catalog import INGREDIENTS_FILE
from app.ingredient_catalog import build_alias_lookup
from app.ingredient_catalog import normalize_ingredient
from app.ingredient_catalog import load_ingredients


RECIPES_FILE = Path(__file__).resolve().parents[1] / "data" / "recipes.json"


def load_recipes(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as recipe_file:
        recipes = json.load(recipe_file)

    if not isinstance(recipes, list):
        raise ValueError("Recipe data must be a list.")

    return recipes


def extract_recipe_ingredient_names(recipes: list[dict[str, Any]]) -> list[str]:
    ingredient_names: set[str] = set()

    for recipe in recipes:
        ingredients = recipe.get("ingredients", [])

        if not isinstance(ingredients, list):
            continue

        for ingredient in ingredients:
            if not isinstance(ingredient, str):
                continue

            normalized_ingredient = normalize_ingredient(ingredient)

            if normalized_ingredient:
                ingredient_names.add(normalized_ingredient)

    return sorted(ingredient_names)


def load_raw_ingredients(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as ingredient_file:
        ingredients = json.load(ingredient_file)

    if not isinstance(ingredients, list):
        raise ValueError("Ingredient data must be a list.")

    return [
        ingredient
        for ingredient in ingredients
        if isinstance(ingredient, dict)
    ]


def add_missing_ingredients(
    recipe_names: list[str],
    ingredients_path: Path = INGREDIENTS_FILE,
) -> int:
    existing_ingredients = load_ingredients(ingredients_path)
    existing_names = set(build_alias_lookup(existing_ingredients))
    updated_ingredients = load_raw_ingredients(ingredients_path)
    added_count = 0

    for recipe_name in sorted(set(recipe_names)):
        if recipe_name in existing_names:
            continue

        updated_ingredients.append(
            {
                "name": recipe_name,
                "aliases": [],
                "category": DEFAULT_CATEGORY,
                "warning_tags": [],
                "needs_review": True,
            }
        )
        added_count += 1

    updated_ingredients.sort(key=lambda ingredient: ingredient["name"])
    ingredients_path.write_text(
        json.dumps(updated_ingredients, indent=4) + "\n",
        encoding="utf-8",
    )

    return added_count


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Add missing recipe ingredients to the local ingredient catalogue."
    )
    parser.add_argument("--recipes", type=Path, default=RECIPES_FILE)
    parser.add_argument("--ingredients", type=Path, default=INGREDIENTS_FILE)
    args = parser.parse_args()

    recipe_names = extract_recipe_ingredient_names(load_recipes(args.recipes))
    added_count = add_missing_ingredients(recipe_names, args.ingredients)

    print(f"Added {added_count} missing ingredient(s) to {args.ingredients}.")


if __name__ == "__main__":
    main()
