import json
from pathlib import Path
from typing import Any

from app.recipe_retrieval import SemanticRecipeRetriever


RECIPES_FILE = Path(__file__).resolve().parents[1] / "data" / "recipes.json"


def load_recipes() -> list[dict[str, Any]]:
    with RECIPES_FILE.open(encoding="utf-8") as recipe_file:
        recipes = json.load(recipe_file)

    if not isinstance(recipes, list):
        raise ValueError("Recipe data must be a list.")

    return recipes


def main() -> None:
    retriever = SemanticRecipeRetriever()
    recipe_count = retriever.build_database(load_recipes())
    print(
        f"Built ChromaDB recipe collection '{retriever.collection_name}' "
        f"with {recipe_count} recipes at {retriever.db_path}."
    )


if __name__ == "__main__":
    main()
