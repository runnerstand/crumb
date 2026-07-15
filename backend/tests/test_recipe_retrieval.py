from pathlib import Path

from fastapi.testclient import TestClient

from app import main
from app import recipe_retrieval
from app.main import app


client = TestClient(app)


RECIPES = [
    {
        "id": 1,
        "title": "Tomato Egg Rice",
        "ingredients": ["egg", "tomato", "rice", "cooking oil"],
        "instructions": ["Cook egg.", "Serve with rice."],
        "cooking_time_minutes": 20,
    },
    {
        "id": 2,
        "title": "Garlic Spinach Stir-fry",
        "ingredients": ["spinach", "garlic", "salt", "cooking oil"],
        "instructions": ["Stir fry spinach."],
        "cooking_time_minutes": 10,
    },
]


class FakeCollection:
    def __init__(self) -> None:
        self.upsert_payload = None
        self.query_payload = None

    def upsert(self, **payload) -> None:
        self.upsert_payload = payload

    def query(self, **payload) -> dict[str, list[list[str]]]:
        self.query_payload = payload
        return {"ids": [["recipe-2", "recipe-1"]]}


class FakeClient:
    def __init__(self, collection: FakeCollection) -> None:
        self.collection = collection

    def get_or_create_collection(self, name: str) -> FakeCollection:
        return self.collection

    def get_collection(self, name: str) -> FakeCollection:
        return self.collection


def test_build_database_embeds_recipes_and_upserts_to_chromadb(monkeypatch) -> None:
    collection = FakeCollection()

    def fake_embed_texts(texts: list[str], model_name: str) -> list[list[float]]:
        assert model_name == "fake-model"
        assert "Tomato Egg Rice" in texts[0]
        return [[float(index), 0.1] for index, _ in enumerate(texts)]

    monkeypatch.setattr(recipe_retrieval, "embed_texts", fake_embed_texts)
    monkeypatch.setattr(
        recipe_retrieval,
        "create_chroma_client",
        lambda db_path: FakeClient(collection),
    )

    retriever = recipe_retrieval.SemanticRecipeRetriever(
        db_path=Path("fake-db"),
        collection_name="fake-recipes",
        model_name="fake-model",
    )

    assert retriever.build_database(RECIPES) == 2
    assert collection.upsert_payload["ids"] == ["recipe-1", "recipe-2"]
    assert collection.upsert_payload["embeddings"] == [[0.0, 0.1], [1.0, 0.1]]
    assert collection.upsert_payload["metadatas"][0]["title"] == "Tomato Egg Rice"


def test_retrieve_returns_top_semantic_recipes_from_chromadb(monkeypatch) -> None:
    collection = FakeCollection()

    monkeypatch.setattr(
        recipe_retrieval,
        "embed_texts",
        lambda texts, model_name: [[0.5, 0.2]],
    )
    monkeypatch.setattr(
        recipe_retrieval,
        "create_chroma_client",
        lambda db_path: FakeClient(collection),
    )

    retriever = recipe_retrieval.SemanticRecipeRetriever()
    result = retriever.retrieve(["leafy greens", "garlic"], RECIPES, top_k=2)

    assert result.fallback_reason is None
    assert [recipe["title"] for recipe in result.recipes] == [
        "Garlic Spinach Stir-fry",
        "Tomato Egg Rice",
    ]
    assert collection.query_payload["n_results"] == 2
    assert collection.query_payload["query_embeddings"] == [[0.5, 0.2]]


def test_retrieve_returns_clear_fallback_reason_when_chromadb_unavailable(
    monkeypatch,
) -> None:
    def fake_create_chroma_client(db_path):
        raise RuntimeError("ChromaDB is not installed.")

    monkeypatch.setattr(
        recipe_retrieval,
        "create_chroma_client",
        fake_create_chroma_client,
    )

    result = recipe_retrieval.SemanticRecipeRetriever().retrieve(
        ["egg"],
        RECIPES,
    )

    assert result.recipes == []
    assert result.fallback_reason is not None
    assert "Semantic recipe retrieval unavailable" in result.fallback_reason


def test_generate_recipe_uses_semantic_grounding_when_available(
    monkeypatch,
) -> None:
    captured_prompt = {}

    def fake_call_ollama(prompt: str) -> str:
        captured_prompt["value"] = prompt
        return """
        {
          "title": "AI Spinach Bowl",
          "ingredients": ["spinach", "garlic"],
          "missing_ingredients": [],
          "instructions": ["Cook spinach with garlic."],
          "cooking_time_minutes": 10,
          "warning_tags": [],
          "generated_by_ai": true
        }
        """

    semantic_match = main.build_recipe_match(RECIPES[1], {"spinach", "garlic"})

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)
    monkeypatch.setattr(
        main,
        "build_semantic_recipe_matches",
        lambda request: ([semantic_match], None),
    )

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["spinach", "garlic"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["grounding_source"] == "semantic"
    assert body["title"] == "AI Spinach Bowl"
    assert "Garlic Spinach Stir-fry" in captured_prompt["value"]


def test_semantic_recipe_matches_receive_filtered_recipes() -> None:
    captured_recipes = {}

    class FakeRetriever:
        def retrieve(self, ingredients, recipes, top_k=5):
            captured_recipes["recipes"] = recipes
            return recipe_retrieval.SemanticRecipeResult(recipes)

    request = main.RecipeMatchRequest(
        ingredients=["egg", "spinach"],
        excluded_warning_tags=["egg"],
    )

    matches, fallback_reason = main.build_semantic_recipe_matches(
        request,
        retriever=FakeRetriever(),
    )

    assert fallback_reason is None
    assert all(
        "egg" not in recipe["ingredients"]
        for recipe in captured_recipes["recipes"]
    )
    assert all("eggs" not in match["warning_tags"] for match in matches)


def test_generate_recipe_falls_back_to_ranked_grounding_when_chromadb_unavailable(
    monkeypatch,
) -> None:
    def fake_call_ollama(prompt: str) -> str:
        raise RuntimeError("Ollama is unavailable.")

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)
    monkeypatch.setattr(
        main,
        "build_semantic_recipe_matches",
        lambda request: ([], "Semantic recipe retrieval unavailable: test"),
    )

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Tomato Egg Rice"
    assert body["grounding_source"] == "ranked"
    assert body["retrieval_fallback_reason"] == (
        "Semantic recipe retrieval unavailable: test"
    )
