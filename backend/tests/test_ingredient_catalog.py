import json

from app.extract_ingredients import add_missing_ingredients
from app.extract_ingredients import extract_recipe_ingredient_names
from app.ingredient_catalog import build_warning_tags
from app.ingredient_catalog import canonicalize_ingredient
from app.ingredient_catalog import load_ingredients


def test_extract_recipe_ingredient_names_returns_unique_normalized_names() -> None:
    recipes = [
        {"ingredients": [" Egg ", "tomato", "egg"]},
        {"ingredients": ["TOMATO", "rice", None]},
        {"ingredients": "not a list"},
    ]

    assert extract_recipe_ingredient_names(recipes) == ["egg", "rice", "tomato"]


def test_add_missing_ingredients_preserves_existing_reviewed_data(tmp_path) -> None:
    ingredients_path = tmp_path / "ingredients.json"
    ingredients_path.write_text(
        json.dumps(
            [
                {
                    "name": "egg",
                    "aliases": ["eggs"],
                    "category": "protein",
                    "warning_tags": ["eggs"],
                    "needs_review": False,
                }
            ],
            indent=4,
        )
        + "\n",
        encoding="utf-8",
    )

    added_count = add_missing_ingredients(
        ["egg", "eggs", "tomato"],
        ingredients_path,
    )

    body = json.loads(ingredients_path.read_text(encoding="utf-8"))

    assert added_count == 1
    assert body[0] == {
        "name": "egg",
        "aliases": ["eggs"],
        "category": "protein",
        "warning_tags": ["eggs"],
        "needs_review": False,
    }
    assert body[1] == {
        "name": "tomato",
        "aliases": [],
        "category": "uncategorized",
        "warning_tags": [],
        "needs_review": True,
    }


def test_catalogue_aliases_canonicalize_ingredients() -> None:
    ingredients = load_ingredients()

    assert canonicalize_ingredient(" Eggs ", ingredients) == "egg"
    assert canonicalize_ingredient("soya sauce", ingredients) == "soy sauce"
    assert canonicalize_ingredient("unknown item", ingredients) == "unknown item"


def test_warning_tags_come_from_catalogue_aliases() -> None:
    assert build_warning_tags(["eggs", "green peas", "soya sauce"]) == [
        "eggs",
        "legumes",
        "soy",
    ]
