from typing import Any

from fastapi import APIRouter
from fastapi import Query

from app.ingredient_catalog import build_ingredient_categories
from app.ingredient_catalog import build_ingredient_names
from app.ingredient_catalog import load_ingredients
from app.ingredient_catalog import normalize_ingredient
from app.measurement_conversion import MeasurementSystem
from app.routers.recipes import IngredientSubstitutionRead
from app.routers.recipes import get_suitable_substitutions
from app.routers.recipes import normalize_excluded_warning_tags


router = APIRouter()


@router.get("/ingredients/suggestions")
def suggest_ingredients(q: str = "") -> list[str]:
    query = normalize_ingredient(q)

    if len(query) < 2:
        return []

    catalogue = load_ingredients()
    suggestion_terms = [
        (ingredient.name, ingredient.name)
        for ingredient in catalogue
    ] + [
        (alias, ingredient.name)
        for ingredient in catalogue
        for alias in ingredient.aliases
    ]
    ingredient_names = build_ingredient_names(catalogue)
    prefix_matches = [
        name
        for name in ingredient_names
        if any(
            search_term.startswith(query) and canonical_name == name
            for search_term, canonical_name in suggestion_terms
        )
    ]
    partial_matches = [
        name
        for name in ingredient_names
        if name not in prefix_matches
        and any(
            query in search_term and not search_term.startswith(query)
            and canonical_name == name
            for search_term, canonical_name in suggestion_terms
        )
    ]

    return [*prefix_matches, *partial_matches][:5]


@router.get("/ingredients/categories")
def list_ingredient_categories() -> list[dict[str, Any]]:
    return build_ingredient_categories()


@router.get(
    "/ingredients/{ingredient_name}/substitutions",
    response_model=list[IngredientSubstitutionRead],
)
def list_ingredient_substitutions(
    ingredient_name: str,
    excluded_warning_tags: list[str] = Query(default=[]),
    measurement_system: MeasurementSystem = Query(default="metric"),
) -> list[dict[str, Any]]:
    return get_suitable_substitutions(
        ingredient_name,
        normalize_excluded_warning_tags(excluded_warning_tags),
        measurement_system,
    )
