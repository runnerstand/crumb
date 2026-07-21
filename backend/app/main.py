from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.database import init_db
from app.recipe_images import UPLOAD_ROOT
from app.recipe_images import UPLOAD_ROUTE
from app.recipe_images import ensure_recipe_upload_dir
from app.routers import community
from app.routers import ingredients
from app.routers import recipes
from app.routers import saved_recipes
from app.routers.community import LOCAL_CREATOR_ID
from app.routers.community import LOCAL_CREATOR_NAME
from app.routers.community import normalize_supported_community_ingredients
from app.routers.recipes import ALLOWED_EXCLUDED_WARNING_TAGS
from app.routers.recipes import OLLAMA_BASE_URL
from app.routers.recipes import OLLAMA_MODEL
from app.routers.recipes import OLLAMA_TEMPERATURE
from app.routers.recipes import OLLAMA_TIMEOUT_SECONDS
from app.routers.recipes import RECIPES_FILE
from app.routers.recipes import SUBSTITUTIONS_FILE
from app.routers.recipes import WARNING_TAG_ALIASES
from app.routers.recipes import GeneratedRecipe
from app.routers.recipes import IngredientSubstitutionRead
from app.routers.recipes import RecipeMatchRequest
from app.routers.recipes import SelectedSubstitution
from app.routers.recipes import build_empty_match_response
from app.routers.recipes import build_fallback_generated_recipe
from app.routers.recipes import build_generation_prompt
from app.routers.recipes import build_match_response
from app.routers.recipes import build_ranked_recipe_matches
from app.routers.recipes import build_recipe_match
from app.routers.recipes import build_semantic_recipe_matches
from app.routers.recipes import build_valid_selected_substitutions
from app.routers.recipes import call_ollama
from app.routers.recipes import convert_measurement_lines
from app.routers.recipes import deduplicate_strings_case_insensitively
from app.routers.recipes import generate_recipe_with_ollama
from app.routers.recipes import get_suitable_substitutions
from app.routers.recipes import load_recipes
from app.routers.recipes import load_substitutions
from app.routers.recipes import match_passes_filters
from app.routers.recipes import normalize_excluded_warning_tags
from app.routers.recipes import normalize_request_ingredients
from app.routers.recipes import normalize_substitution
from app.routers.recipes import post_process_generated_recipe
from app.routers.recipes import recipe_is_allowed
from app.routers.recipes import recipe_measurement_lines
from app.routers.recipes import recipe_warning_tags
from app.routers.recipes import recipe_with_measurements
from app.routers.recipes import warning_tags_are_allowed


app = FastAPI(title="Smart Pantry API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:19006",
        "http://127.0.0.1:19006",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
    ],
    allow_origin_regex=r"http://(localhost|127\.0\.0\.1)(:\d+)?",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

init_db()
ensure_recipe_upload_dir()

app.mount(
    UPLOAD_ROUTE,
    StaticFiles(directory=UPLOAD_ROOT),
    name="recipe_uploads",
)

app.include_router(ingredients.router)
app.include_router(recipes.router)
app.include_router(saved_recipes.router)
app.include_router(community.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
