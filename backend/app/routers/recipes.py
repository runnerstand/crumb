import json
import os
import sys
from pathlib import Path
from typing import Any
from urllib import error
from urllib import request as urllib_request

from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from pydantic import BaseModel
from pydantic import Field
from pydantic import field_validator
from sqlalchemy.orm import Session
from sqlalchemy.orm import selectinload

from app.ingredient_catalog import build_warning_tags
from app.ingredient_catalog import canonicalize_ingredient
from app.ingredient_catalog import load_ingredients
from app.ingredient_catalog import normalize_ingredient
from app.measurement_conversion import MeasurementSystem
from app.measurement_conversion import convert_measurement_text
from app.database import get_db
from app.models import Ingredient
from app.models import LOCAL_USER_ID
from app.models import LOCAL_USER_NAME
from app.models import Recipe
from app.models import RecipeIngredient
from app.models import User
from app.recipe_retrieval import SemanticRecipeRetriever
from app.schemas import RecipeCreate
from app.schemas import RecipeRead
from app.schemas import RecipeUpdate


router = APIRouter()

RECIPES_FILE = Path(__file__).resolve().parents[2] / "data" / "recipes.json"
SUBSTITUTIONS_FILE = (
    Path(__file__).resolve().parents[2] / "data" / "ingredient_substitutions.json"
)
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2")
OLLAMA_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "20"))
OLLAMA_TEMPERATURE = 0.2
ALLOWED_EXCLUDED_WARNING_TAGS = {
    "dairy",
    "egg",
    "eggs",
    "soy",
    "nuts",
    "gluten",
    "seafood",
    "shellfish",
    "sesame",
    "legumes",
}
WARNING_TAG_ALIASES = {
    "egg": "eggs",
}


def ensure_local_user(db: Session) -> None:
    if db.get(User, LOCAL_USER_ID) is not None:
        return

    db.add(User(id=LOCAL_USER_ID, display_name=LOCAL_USER_NAME))
    db.flush()


def serialize_recipe(recipe: Recipe) -> dict[str, Any]:
    return {
        "id": recipe.id,
        "user_id": recipe.user_id,
        "title": recipe.title,
        "ingredients": [
            {
                "ingredient_id": recipe_ingredient.ingredient_id,
                "ingredient_name": recipe_ingredient.ingredient.name,
                "quantity": recipe_ingredient.quantity,
                "unit": recipe_ingredient.unit,
            }
            for recipe_ingredient in sorted(
                recipe.ingredients,
                key=lambda item: item.ingredient.name,
            )
        ],
        "instructions": recipe.instructions,
        "cooking_time_minutes": recipe.cooking_time_minutes,
        "created_at": recipe.created_at,
    }


def get_recipe_or_404(recipe_id: int, db: Session) -> Recipe:
    recipe = (
        db.query(Recipe)
        .options(selectinload(Recipe.ingredients).selectinload(RecipeIngredient.ingredient))
        .filter(Recipe.id == recipe_id)
        .first()
    )

    if recipe is None:
        raise HTTPException(status_code=404, detail="Recipe not found.")

    return recipe


def find_supported_ingredient(ingredient_name: str, db: Session) -> Ingredient:
    catalogue = load_ingredients()
    normalized_ingredient = canonicalize_ingredient(ingredient_name, catalogue)
    catalogue_by_name = {ingredient.name: ingredient for ingredient in catalogue}

    if not normalized_ingredient or normalized_ingredient not in catalogue_by_name:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported ingredient: {ingredient_name.strip()}",
        )

    ingredient = db.query(Ingredient).filter(Ingredient.name == normalized_ingredient).first()

    if ingredient is not None:
        return ingredient

    catalogue_ingredient = catalogue_by_name[normalized_ingredient]
    ingredient = Ingredient(
        name=catalogue_ingredient.name,
        category=catalogue_ingredient.category,
        aliases=list(catalogue_ingredient.aliases),
        warning_tags=list(catalogue_ingredient.warning_tags),
        needs_review=catalogue_ingredient.needs_review,
    )
    db.add(ingredient)
    db.flush()

    return ingredient


def replace_recipe_ingredients(
    recipe: Recipe,
    payload: RecipeCreate | RecipeUpdate,
    db: Session,
) -> None:
    recipe.ingredients.clear()
    seen_ingredient_ids: set[int] = set()

    for requested_ingredient in payload.ingredients:
        ingredient = find_supported_ingredient(requested_ingredient.ingredient_name, db)

        if ingredient.id in seen_ingredient_ids:
            raise HTTPException(
                status_code=400,
                detail=f"Duplicate ingredient: {ingredient.name}",
            )

        seen_ingredient_ids.add(ingredient.id)
        recipe.ingredients.append(
            RecipeIngredient(
                ingredient_id=ingredient.id,
                quantity=requested_ingredient.quantity,
                unit=requested_ingredient.unit,
            )
        )


@router.post("/recipes/user-created", response_model=RecipeRead, status_code=201)
def create_user_recipe(
    recipe_request: RecipeCreate,
    db: Session = Depends(get_db),
) -> dict[str, Any]:
    ensure_local_user(db)
    recipe = Recipe(
        user_id=LOCAL_USER_ID,
        title=recipe_request.title,
        instructions=recipe_request.instructions,
        cooking_time_minutes=recipe_request.cooking_time_minutes,
    )
    replace_recipe_ingredients(recipe, recipe_request, db)

    db.add(recipe)
    db.commit()
    db.refresh(recipe)

    return serialize_recipe(get_recipe_or_404(recipe.id, db))


@router.get("/recipes/user-created", response_model=list[RecipeRead])
def list_user_recipes(db: Session = Depends(get_db)) -> list[dict[str, Any]]:
    recipes = (
        db.query(Recipe)
        .options(selectinload(Recipe.ingredients).selectinload(RecipeIngredient.ingredient))
        .filter(Recipe.user_id == LOCAL_USER_ID)
        .order_by(Recipe.created_at.desc())
        .all()
    )

    return [serialize_recipe(recipe) for recipe in recipes]


@router.get("/recipes/user-created/{recipe_id}", response_model=RecipeRead)
def read_user_recipe(
    recipe_id: int,
    db: Session = Depends(get_db),
) -> dict[str, Any]:
    recipe = get_recipe_or_404(recipe_id, db)

    if recipe.user_id != LOCAL_USER_ID:
        raise HTTPException(status_code=403, detail="Only the recipe owner can view this recipe.")

    return serialize_recipe(recipe)


@router.patch("/recipes/user-created/{recipe_id}", response_model=RecipeRead)
def update_user_recipe(
    recipe_id: int,
    recipe_request: RecipeUpdate,
    db: Session = Depends(get_db),
) -> dict[str, Any]:
    recipe = get_recipe_or_404(recipe_id, db)

    if recipe.user_id != LOCAL_USER_ID:
        raise HTTPException(status_code=403, detail="Only the recipe owner can update this recipe.")

    recipe.title = recipe_request.title
    recipe.instructions = recipe_request.instructions
    recipe.cooking_time_minutes = recipe_request.cooking_time_minutes
    replace_recipe_ingredients(recipe, recipe_request, db)

    db.commit()
    db.refresh(recipe)

    return serialize_recipe(get_recipe_or_404(recipe.id, db))


@router.delete("/recipes/user-created/{recipe_id}")
def delete_user_recipe(
    recipe_id: int,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    recipe = get_recipe_or_404(recipe_id, db)

    if recipe.user_id != LOCAL_USER_ID:
        raise HTTPException(status_code=403, detail="Only the recipe owner can delete this recipe.")

    db.delete(recipe)
    db.commit()

    return {"message": "Recipe deleted."}


class RecipeMatchRequest(BaseModel):
    ingredients: list[str]
    max_cooking_time_minutes: int | None = None
    max_missing_ingredients: int | None = None
    excluded_warning_tags: list[str] = Field(default_factory=list)
    selected_substitutions: list["SelectedSubstitution"] = Field(default_factory=list)
    measurement_system: MeasurementSystem = "metric"

    @field_validator("excluded_warning_tags")
    @classmethod
    def excluded_warning_tags_must_be_supported(
        cls,
        values: list[str],
    ) -> list[str]:
        normalized_values: list[str] = []

        for value in values:
            normalized_value = normalize_ingredient(value)

            if normalized_value not in ALLOWED_EXCLUDED_WARNING_TAGS:
                raise ValueError(f"Unsupported warning tag: {value}")

            canonical_value = WARNING_TAG_ALIASES.get(normalized_value, normalized_value)

            if canonical_value not in normalized_values:
                normalized_values.append(canonical_value)

        return normalized_values

    @field_validator("measurement_system")
    @classmethod
    def measurement_system_must_be_supported(cls, value: str) -> str:
        if value not in {"metric", "us"}:
            raise ValueError("Measurement system must be metric or us.")

        return value


class SelectedSubstitution(BaseModel):
    original_ingredient: str = Field(min_length=1)
    alternative_ingredient: str = Field(min_length=1)

    @field_validator("original_ingredient", "alternative_ingredient")
    @classmethod
    def ingredient_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()

        if not cleaned_value:
            raise ValueError("Ingredient must not be blank.")

        return cleaned_value


class GeneratedRecipe(BaseModel):
    title: str = Field(min_length=1)
    ingredients: list[str] = Field(default_factory=list)
    missing_ingredients: list[str] = Field(default_factory=list)
    instructions: list[str] = Field(min_length=1)
    cooking_time_minutes: int = Field(gt=0)
    warning_tags: list[str] = Field(default_factory=list)
    ingredient_measurements: list[Any] = Field(default_factory=list)
    generated_by_ai: bool
    grounding_source: str | None = None
    retrieval_fallback_reason: str | None = None


class IngredientSubstitutionRead(BaseModel):
    original_ingredient: str
    alternative_ingredient: str
    alternative_measurement: dict[str, Any] | None = None
    note: str = ""
    warning_tags: list[str] = Field(default_factory=list)
    dietary_tags: list[str] = Field(default_factory=list)


def _main_override(name: str) -> Any:
    main_module = sys.modules.get("app.main")
    if main_module is None:
        return globals()[name]

    return getattr(main_module, name, globals()[name])


def load_recipes() -> list[dict[str, Any]]:
    with RECIPES_FILE.open(encoding="utf-8") as recipe_file:
        recipes = json.load(recipe_file)

    if not isinstance(recipes, list):
        raise ValueError("Recipe data must be a list.")

    return recipes


def load_substitutions() -> list[dict[str, Any]]:
    with SUBSTITUTIONS_FILE.open(encoding="utf-8") as substitutions_file:
        substitutions = json.load(substitutions_file)

    if not isinstance(substitutions, list):
        raise ValueError("Substitution data must be a list.")

    return [substitution for substitution in substitutions if isinstance(substitution, dict)]


def deduplicate_strings_case_insensitively(values: list[str]) -> list[str]:
    deduplicated_values: dict[str, str] = {}
    catalogue = load_ingredients()

    for value in values:
        normalized_value = canonicalize_ingredient(value, catalogue)

        if normalized_value and normalized_value not in deduplicated_values:
            deduplicated_values[normalized_value] = normalized_value

    return list(deduplicated_values.values())


def post_process_generated_recipe(
    generated_recipe: GeneratedRecipe,
    pantry_ingredients: list[str],
    measurement_system: MeasurementSystem = "metric",
) -> GeneratedRecipe:
    normalized_pantry_ingredients = set(pantry_ingredients)
    generated_recipe.ingredients = deduplicate_strings_case_insensitively(
        generated_recipe.ingredients
    )
    normalized_generated_ingredients = [
        normalize_ingredient(ingredient) for ingredient in generated_recipe.ingredients
    ]
    generated_recipe.missing_ingredients = [
        ingredient
        for ingredient in normalized_generated_ingredients
        if ingredient not in normalized_pantry_ingredients
    ]
    generated_recipe.warning_tags = build_warning_tags(normalized_generated_ingredients)
    generated_recipe.ingredient_measurements = convert_measurement_lines(
        generated_recipe.ingredient_measurements
        if generated_recipe.ingredient_measurements
        else generated_recipe.ingredients,
        measurement_system,
    )

    return generated_recipe


def convert_measurement_lines(
    values: list[Any],
    measurement_system: MeasurementSystem,
) -> list[dict[str, Any]]:
    converted_measurements: list[dict[str, Any]] = []

    for value in values:
        original_text = str(value)

        if isinstance(value, dict):
            original_text = str(
                value.get("original_text")
                or value.get("text")
                or value.get("display")
                or ""
            )

        if not original_text.strip():
            continue

        converted = convert_measurement_text(original_text, measurement_system)
        converted_measurements.append(
            {
                "original_text": converted.original_text,
                "text": converted.text,
                "original_quantity": converted.original_quantity,
                "original_unit": converted.original_unit,
                "converted_quantity": converted.converted_quantity,
                "converted_unit": converted.converted_unit,
            }
        )

    return converted_measurements


def recipe_measurement_lines(recipe: dict[str, Any]) -> list[str]:
    measurements = recipe.get("ingredient_measurements")

    if isinstance(measurements, list):
        return [str(measurement) for measurement in measurements]

    return [
        ingredient
        for ingredient in recipe.get("ingredients", [])
        if isinstance(ingredient, str)
    ]


def recipe_with_measurements(
    recipe: dict[str, Any],
    measurement_system: MeasurementSystem,
) -> dict[str, Any]:
    return {
        **recipe,
        "ingredient_measurements": convert_measurement_lines(
            recipe_measurement_lines(recipe),
            measurement_system,
        ),
    }


def recipe_warning_tags(recipe: dict[str, Any]) -> list[str]:
    return build_warning_tags(
        [
            ingredient
            for ingredient in recipe.get("ingredients", [])
            if isinstance(ingredient, str)
        ]
    )


def warning_tags_are_allowed(
    warning_tags: list[str],
    excluded_warning_tags: list[str],
) -> bool:
    return not set(warning_tags).intersection(excluded_warning_tags)


def normalize_excluded_warning_tags(values: list[str]) -> list[str]:
    normalized_values: list[str] = []

    for value in values:
        normalized_value = normalize_ingredient(value)

        if normalized_value not in ALLOWED_EXCLUDED_WARNING_TAGS:
            raise HTTPException(
                status_code=422,
                detail=f"Unsupported warning tag: {value}",
            )

        canonical_value = WARNING_TAG_ALIASES.get(normalized_value, normalized_value)

        if canonical_value not in normalized_values:
            normalized_values.append(canonical_value)

    return normalized_values


def recipe_is_allowed(
    recipe: dict[str, Any],
    excluded_warning_tags: list[str],
) -> bool:
    return warning_tags_are_allowed(recipe_warning_tags(recipe), excluded_warning_tags)


def normalize_substitution(raw_substitution: dict[str, Any]) -> dict[str, Any] | None:
    catalogue = load_ingredients()
    original_ingredient = canonicalize_ingredient(
        str(raw_substitution.get("original_ingredient", "")),
        catalogue,
    )
    alternative_ingredient = canonicalize_ingredient(
        str(raw_substitution.get("alternative_ingredient", "")),
        catalogue,
    )

    if not original_ingredient or not alternative_ingredient:
        return None

    warning_tags = [
        normalize_ingredient(str(tag))
        for tag in raw_substitution.get("warning_tags", [])
        if isinstance(tag, str) and normalize_ingredient(str(tag))
    ]
    dietary_tags = [
        normalize_ingredient(str(tag))
        for tag in raw_substitution.get("dietary_tags", [])
        if isinstance(tag, str) and normalize_ingredient(str(tag))
    ]
    note = raw_substitution.get("note", "")
    alternative_measurement = raw_substitution.get("alternative_measurement")

    return {
        "original_ingredient": original_ingredient,
        "alternative_ingredient": alternative_ingredient,
        "alternative_measurement": (
            str(alternative_measurement).strip()
            if isinstance(alternative_measurement, str)
            else None
        ),
        "note": note.strip() if isinstance(note, str) else "",
        "warning_tags": sorted(set(warning_tags)),
        "dietary_tags": sorted(set(dietary_tags)),
    }


def get_suitable_substitutions(
    ingredient_name: str,
    excluded_warning_tags: list[str] | None = None,
    measurement_system: MeasurementSystem = "metric",
) -> list[dict[str, Any]]:
    catalogue = load_ingredients()
    normalized_ingredient = canonicalize_ingredient(ingredient_name, catalogue)
    excluded_tags = excluded_warning_tags or []
    suitable_substitutions: list[dict[str, Any]] = []

    for raw_substitution in load_substitutions():
        substitution = normalize_substitution(raw_substitution)

        if substitution is None:
            continue

        if substitution["original_ingredient"] != normalized_ingredient:
            continue

        if not warning_tags_are_allowed(substitution["warning_tags"], excluded_tags):
            continue

        if substitution["alternative_measurement"] is not None:
            substitution["alternative_measurement"] = convert_measurement_lines(
                [substitution["alternative_measurement"]],
                measurement_system,
            )[0]

        suitable_substitutions.append(substitution)

    return suitable_substitutions


def build_valid_selected_substitutions(
    selected_substitutions: list[SelectedSubstitution],
    excluded_warning_tags: list[str],
) -> list[dict[str, str]]:
    valid_substitutions: list[dict[str, str]] = []

    for selected_substitution in selected_substitutions:
        suitable_substitutions = get_suitable_substitutions(
            selected_substitution.original_ingredient,
            excluded_warning_tags,
            "metric",
        )
        requested_alternative = canonicalize_ingredient(
            selected_substitution.alternative_ingredient
        )

        for suitable_substitution in suitable_substitutions:
            if suitable_substitution["alternative_ingredient"] != requested_alternative:
                continue

            valid_substitutions.append(
                {
                    "original_ingredient": suitable_substitution["original_ingredient"],
                    "alternative_ingredient": suitable_substitution[
                        "alternative_ingredient"
                    ],
                }
            )
            break

    return valid_substitutions


def build_recipe_match(recipe: dict[str, Any], pantry_ingredients: set[str]) -> dict[str, Any]:
    catalogue = load_ingredients()
    recipe_ingredients = [
        canonicalize_ingredient(ingredient, catalogue)
        for ingredient in recipe.get("ingredients", [])
        if isinstance(ingredient, str)
    ]
    matched_ingredients = [
        ingredient for ingredient in recipe_ingredients if ingredient in pantry_ingredients
    ]
    missing_ingredients = [
        ingredient for ingredient in recipe_ingredients if ingredient not in pantry_ingredients
    ]
    match_percentage = 0

    if recipe_ingredients:
        match_percentage = round(len(matched_ingredients) / len(recipe_ingredients) * 100)

    return {
        "recipe": recipe,
        "score": len(matched_ingredients),
        "matched_ingredients": matched_ingredients,
        "missing_ingredients": missing_ingredients,
        "match_percentage": match_percentage,
        "warning_tags": build_warning_tags(recipe_ingredients),
    }


def match_passes_filters(match: dict[str, Any], request: RecipeMatchRequest) -> bool:
    recipe = match["recipe"]

    if (
        request.max_cooking_time_minutes is not None
        and recipe.get("cooking_time_minutes", 0) > request.max_cooking_time_minutes
    ):
        return False

    if (
        request.max_missing_ingredients is not None
        and len(match["missing_ingredients"]) > request.max_missing_ingredients
    ):
        return False

    if not warning_tags_are_allowed(
        match["warning_tags"],
        request.excluded_warning_tags,
    ):
        return False

    return True


def build_ranked_recipe_matches(request: RecipeMatchRequest) -> list[dict[str, Any]]:
    catalogue = load_ingredients()
    pantry_ingredients = {
        normalized
        for normalized in (
            canonicalize_ingredient(ingredient, catalogue)
            for ingredient in request.ingredients
        )
        if normalized
    }

    if not pantry_ingredients:
        raise HTTPException(
            status_code=400,
            detail="Please provide at least one ingredient.",
        )

    suitable_recipes = [
        recipe
        for recipe in load_recipes()
        if recipe_is_allowed(recipe, request.excluded_warning_tags)
    ]
    recipe_matches = [
        match
        for match in (
            build_recipe_match(recipe, pantry_ingredients) for recipe in suitable_recipes
        )
        if match["score"] > 0 and match_passes_filters(match, request)
    ]

    return sorted(
        recipe_matches,
        key=lambda match: (
            -match["score"],
            len(match["missing_ingredients"]),
            -match["match_percentage"],
            match["recipe"].get("cooking_time_minutes", 0),
            match["recipe"].get("title", ""),
        ),
    )


def normalize_request_ingredients(request: RecipeMatchRequest) -> list[str]:
    catalogue = load_ingredients()
    return [
        normalized
        for normalized in (
            canonicalize_ingredient(ingredient, catalogue)
            for ingredient in request.ingredients
        )
        if normalized
    ]


def build_semantic_recipe_matches(
    request: RecipeMatchRequest,
    retriever: SemanticRecipeRetriever | None = None,
) -> tuple[list[dict[str, Any]], str | None]:
    pantry_ingredients = normalize_request_ingredients(request)
    retriever = retriever or SemanticRecipeRetriever()
    retrieval_result = retriever.retrieve(
        pantry_ingredients,
        [
            recipe
            for recipe in load_recipes()
            if recipe_is_allowed(recipe, request.excluded_warning_tags)
        ],
        top_k=5,
    )

    if retrieval_result.fallback_reason is not None:
        return [], retrieval_result.fallback_reason

    semantic_matches = [
        match
        for match in (
            build_recipe_match(recipe, set(pantry_ingredients))
            for recipe in retrieval_result.recipes
        )
        if match_passes_filters(match, request)
    ]

    if not semantic_matches:
        return [], "Semantic recipe retrieval returned no recipes after filters."

    return semantic_matches, None


def build_empty_match_response() -> dict[str, Any]:
    return {
        "recipe": None,
        "matched_ingredients": [],
        "missing_ingredients": [],
        "match_percentage": 0,
        "warning_tags": [],
        "results": [],
        "message": "No recipe matches the provided ingredients.",
    }


def build_match_response(
    ranked_matches: list[dict[str, Any]],
    measurement_system: MeasurementSystem = "metric",
    excluded_warning_tags: list[str] | None = None,
) -> dict[str, Any]:
    ranked_matches = ranked_matches[:5]
    ranked_matches = [
        {
            **match,
            "recipe": recipe_with_measurements(match["recipe"], measurement_system),
        }
        for match in ranked_matches
    ]
    best_match = ranked_matches[0] if ranked_matches else None

    if best_match is None:
        response = build_empty_match_response()

        if excluded_warning_tags:
            response["message"] = (
                "No suitable recipe matches the provided ingredients and restrictions."
            )

        return response

    response = {
        **best_match,
        "results": ranked_matches,
    }

    if excluded_warning_tags:
        response["message"] = "Some recipes may be excluded by your restrictions."

    return response


def build_fallback_generated_recipe(match: dict[str, Any] | None) -> dict[str, Any]:
    if match is None:
        return {
            "title": "No recipe found",
            "ingredients": [],
            "missing_ingredients": [],
            "instructions": ["Try adding more pantry ingredients."],
            "cooking_time_minutes": 1,
            "warning_tags": [],
            "ingredient_measurements": [],
            "generated_by_ai": False,
        }

    recipe = match["recipe"]

    return {
        "title": recipe["title"],
        "ingredients": recipe["ingredients"],
        "missing_ingredients": match["missing_ingredients"],
        "instructions": recipe["instructions"],
        "cooking_time_minutes": recipe["cooking_time_minutes"],
        "warning_tags": match["warning_tags"],
        "ingredient_measurements": match["recipe"].get("ingredient_measurements", []),
        "generated_by_ai": False,
    }


def build_generation_prompt(
    pantry_ingredients: list[str],
    grounding_matches: list[dict[str, Any]],
    excluded_warning_tags: list[str],
    selected_substitutions: list[dict[str, str]],
    measurement_system: MeasurementSystem,
    validation_error: str | None = None,
) -> str:
    context = [
        {
            "title": match["recipe"]["title"],
            "ingredients": match["recipe"]["ingredients"],
            "missing_ingredients": match["missing_ingredients"],
            "instructions": match["recipe"]["instructions"],
            "cooking_time_minutes": match["recipe"]["cooking_time_minutes"],
            "warning_tags": match["warning_tags"],
        }
        for match in grounding_matches
    ]
    retry_note = (
        f"\nPrevious response validation error: {validation_error}\n"
        if validation_error
        else ""
    )

    return (
        "Create one simple recipe using this pantry and grounded recipe context. "
        "Return only JSON with these keys: title, ingredients, missing_ingredients, "
        "instructions, cooking_time_minutes, warning_tags, generated_by_ai. "
        "generated_by_ai must be true. Keep warning_tags from relevant ingredients. "
        "Do not use ingredients that have any excluded warning tags. "
        f"Prefer {measurement_system} measurements when quantities are useful. "
        f"{retry_note}"
        f"Pantry ingredients: {json.dumps(pantry_ingredients)}\n"
        f"Excluded warning tags: {json.dumps(excluded_warning_tags)}\n"
        f"Selected substitutions: {json.dumps(selected_substitutions)}\n"
        f"Measurement system: {measurement_system}\n"
        f"Grounding recipes: {json.dumps(context)}"
    )


def call_ollama(prompt: str) -> str:
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "format": "json",
        "options": {
            "temperature": OLLAMA_TEMPERATURE,
        },
    }
    request_body = json.dumps(payload).encode("utf-8")
    ollama_request = urllib_request.Request(
        f"{OLLAMA_BASE_URL.rstrip('/')}/api/generate",
        data=request_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib_request.urlopen(
            ollama_request,
            timeout=OLLAMA_TIMEOUT_SECONDS,
        ) as response:
            response_data = json.loads(response.read().decode("utf-8"))
    except (OSError, TimeoutError, error.URLError, json.JSONDecodeError) as exc:
        raise RuntimeError("Ollama is unavailable.") from exc

    generated_text = response_data.get("response")

    if not isinstance(generated_text, str):
        raise RuntimeError("Ollama returned an unreadable response.")

    return generated_text


def generate_recipe_with_ollama(
    pantry_ingredients: list[str],
    grounding_matches: list[dict[str, Any]],
    excluded_warning_tags: list[str],
    selected_substitutions: list[dict[str, str]],
    measurement_system: MeasurementSystem,
) -> GeneratedRecipe:
    validation_error: str | None = None

    for _ in range(2):
        prompt = build_generation_prompt(
            pantry_ingredients,
            grounding_matches,
            excluded_warning_tags,
            selected_substitutions,
            measurement_system,
            validation_error,
        )
        generated_text = _main_override("call_ollama")(prompt)

        try:
            generated_data = json.loads(generated_text)
            generated_recipe = GeneratedRecipe.model_validate(generated_data)
            processed_recipe = post_process_generated_recipe(
                generated_recipe,
                pantry_ingredients,
                measurement_system,
            )

            if not warning_tags_are_allowed(
                processed_recipe.warning_tags,
                excluded_warning_tags,
            ):
                validation_error = (
                    "Generated recipe used ingredients with excluded warning tags."
                )
                continue

            return processed_recipe
        except (json.JSONDecodeError, ValueError) as exc:
            validation_error = str(exc)

    raise RuntimeError("Ollama output did not match the required recipe shape.")


@router.post("/recipes/match")
def match_recipe(request: RecipeMatchRequest) -> dict[str, Any]:
    return build_match_response(
        build_ranked_recipe_matches(request),
        request.measurement_system,
        request.excluded_warning_tags,
    )


@router.post(
    "/recipes/generate",
    response_model=GeneratedRecipe,
    response_model_exclude_none=True,
)
def generate_recipe(request: RecipeMatchRequest) -> GeneratedRecipe:
    ranked_matches = build_ranked_recipe_matches(request)
    semantic_matches, semantic_fallback_reason = _main_override(
        "build_semantic_recipe_matches"
    )(request)
    if request.excluded_warning_tags and not ranked_matches:
        semantic_matches = []
        semantic_fallback_reason = (
            "No suitable recipe matches the provided ingredients and restrictions."
        )
    grounding_matches = semantic_matches[:5]
    grounding_source = "semantic"

    if not grounding_matches:
        grounding_matches = ranked_matches[:3]
        grounding_source = "ranked"

    fallback_recipe = GeneratedRecipe.model_validate(
        build_fallback_generated_recipe(grounding_matches[0] if grounding_matches else None)
    )
    fallback_recipe.ingredient_measurements = convert_measurement_lines(
        fallback_recipe.ingredient_measurements,
        request.measurement_system,
    )
    fallback_recipe.grounding_source = grounding_source
    fallback_recipe.retrieval_fallback_reason = semantic_fallback_reason
    pantry_ingredients = normalize_request_ingredients(request)
    selected_substitutions = build_valid_selected_substitutions(
        request.selected_substitutions,
        request.excluded_warning_tags,
    )

    if not grounding_matches:
        if request.excluded_warning_tags:
            fallback_recipe.retrieval_fallback_reason = (
                "No suitable recipe matches the provided ingredients and restrictions."
            )

        return fallback_recipe

    try:
        generated_recipe = _main_override("generate_recipe_with_ollama")(
            pantry_ingredients,
            grounding_matches,
            request.excluded_warning_tags,
            selected_substitutions,
            request.measurement_system,
        )
        generated_recipe.grounding_source = grounding_source
        generated_recipe.retrieval_fallback_reason = semantic_fallback_reason
        return generated_recipe
    except RuntimeError:
        return fallback_recipe
