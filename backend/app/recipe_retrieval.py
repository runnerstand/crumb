import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any


DEFAULT_CHROMA_DB_PATH = Path(__file__).resolve().parents[1] / "chroma_db"
CHROMA_DB_PATH = Path(os.getenv("CHROMA_DB_PATH", str(DEFAULT_CHROMA_DB_PATH)))
CHROMA_COLLECTION_NAME = os.getenv("CHROMA_COLLECTION_NAME", "smart_pantry_recipes")
EMBEDDING_MODEL_NAME = os.getenv(
    "EMBEDDING_MODEL_NAME",
    "sentence-transformers/all-MiniLM-L6-v2",
)


@dataclass
class SemanticRecipeResult:
    recipes: list[dict[str, Any]]
    fallback_reason: str | None = None


def build_recipe_embedding_text(recipe: dict[str, Any]) -> str:
    title = recipe.get("title", "")
    ingredients = recipe.get("ingredients", [])
    instructions = recipe.get("instructions", [])

    ingredient_text = ", ".join(
        ingredient for ingredient in ingredients if isinstance(ingredient, str)
    )
    instruction_text = " ".join(
        instruction for instruction in instructions if isinstance(instruction, str)
    )

    return (
        f"Title: {title}\n"
        f"Ingredients: {ingredient_text}\n"
        f"Instructions: {instruction_text}"
    )


def build_query_text(ingredients: list[str]) -> str:
    return f"Pantry ingredients: {', '.join(ingredients)}"


def coerce_embedding(embedding: Any) -> list[float]:
    if hasattr(embedding, "tolist"):
        embedding = embedding.tolist()

    return [float(value) for value in embedding]


@lru_cache(maxsize=2)
def load_embedding_model(model_name: str) -> Any:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise RuntimeError("sentence-transformers is not installed.") from exc

    return SentenceTransformer(model_name)


def embed_texts(texts: list[str], model_name: str) -> list[list[float]]:
    model = load_embedding_model(model_name)
    embeddings = model.encode(texts, normalize_embeddings=True)

    return [coerce_embedding(embedding) for embedding in embeddings]


def create_chroma_client(db_path: Path) -> Any:
    try:
        import chromadb
    except ImportError as exc:
        raise RuntimeError("ChromaDB is not installed.") from exc

    return chromadb.PersistentClient(path=str(db_path))


class SemanticRecipeRetriever:
    def __init__(
        self,
        db_path: Path = CHROMA_DB_PATH,
        collection_name: str = CHROMA_COLLECTION_NAME,
        model_name: str = EMBEDDING_MODEL_NAME,
    ) -> None:
        self.db_path = db_path
        self.collection_name = collection_name
        self.model_name = model_name

    def build_database(self, recipes: list[dict[str, Any]]) -> int:
        documents = [build_recipe_embedding_text(recipe) for recipe in recipes]
        embeddings = embed_texts(documents, self.model_name)
        ids = [self._recipe_document_id(recipe, index) for index, recipe in enumerate(recipes)]
        metadatas = [
            {
                "recipe_id": recipe.get("id", index),
                "title": str(recipe.get("title", "")),
            }
            for index, recipe in enumerate(recipes)
        ]

        client = create_chroma_client(self.db_path)
        collection = client.get_or_create_collection(name=self.collection_name)
        collection.upsert(
            ids=ids,
            documents=documents,
            embeddings=embeddings,
            metadatas=metadatas,
        )

        return len(recipes)

    def retrieve(
        self,
        ingredients: list[str],
        recipes: list[dict[str, Any]],
        top_k: int = 5,
    ) -> SemanticRecipeResult:
        if not ingredients:
            return SemanticRecipeResult([], "No pantry ingredients were provided.")

        try:
            client = create_chroma_client(self.db_path)
            collection = client.get_collection(name=self.collection_name)
            query_embedding = embed_texts([build_query_text(ingredients)], self.model_name)[0]
            results = collection.query(
                query_embeddings=[query_embedding],
                n_results=top_k,
            )
        except Exception as exc:
            return SemanticRecipeResult(
                [],
                f"Semantic recipe retrieval unavailable: {exc}",
            )

        recipe_by_document_id = {
            self._recipe_document_id(recipe, index): recipe
            for index, recipe in enumerate(recipes)
        }
        semantic_recipes: list[dict[str, Any]] = []

        for document_id in self._result_ids(results):
            recipe = recipe_by_document_id.get(document_id)

            if recipe is not None:
                semantic_recipes.append(recipe)

        if not semantic_recipes:
            return SemanticRecipeResult(
                [],
                "Semantic recipe retrieval returned no usable recipes.",
            )

        return SemanticRecipeResult(semantic_recipes)

    @staticmethod
    def _recipe_document_id(recipe: dict[str, Any], index: int) -> str:
        recipe_id = recipe.get("id", index)

        return f"recipe-{recipe_id}"

    @staticmethod
    def _result_ids(results: dict[str, Any]) -> list[str]:
        ids = results.get("ids", [])

        if not ids or not isinstance(ids, list) or not isinstance(ids[0], list):
            return []

        return [document_id for document_id in ids[0] if isinstance(document_id, str)]
