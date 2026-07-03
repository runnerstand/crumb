from fastapi.testclient import TestClient

from app import main
from app.main import app


client = TestClient(app)


def test_match_recipe_returns_ranked_recipes_with_match_details() -> None:
    response = client.post(
        "/recipes/match",
        json={"ingredients": [" Egg ", "TOMATO", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["recipe"]["title"] == "Tomato Egg Rice"
    assert body["matched_ingredients"] == ["egg", "tomato", "rice"]
    assert body["missing_ingredients"] == ["cooking oil"]
    assert body["match_percentage"] == 75
    assert body["warning_tags"] == ["eggs"]
    assert len(body["results"]) == 5
    assert body["results"][0]["recipe"]["title"] == "Tomato Egg Rice"
    assert body["results"][0]["match_percentage"] == 75
    assert body["results"][0]["matched_ingredients"] == ["egg", "tomato", "rice"]
    assert body["results"][0]["missing_ingredients"] == ["cooking oil"]


def test_match_recipe_preserves_behavior_without_restrictions() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg", "tomato", "rice"],
            "excluded_warning_tags": [],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["recipe"]["title"] == "Tomato Egg Rice"
    assert body["warning_tags"] == ["eggs"]


def test_match_recipe_returns_metric_measurements_by_default() -> None:
    response = client.post(
        "/recipes/match",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    measurements = response.json()["recipe"]["ingredient_measurements"]
    assert measurements[1]["original_text"] == "150 grams tomato"
    assert measurements[1]["text"] == "150 grams tomato"


def test_match_recipe_returns_us_measurements_when_requested() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg", "tomato", "rice"],
            "measurement_system": "us",
        },
    )

    assert response.status_code == 200
    measurements = response.json()["recipe"]["ingredient_measurements"]
    assert measurements[1]["original_text"] == "150 grams tomato"
    assert measurements[1]["text"] == "5.3 ounces tomato"


def test_match_recipe_filters_one_restriction() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg", "tomato", "rice", "spinach", "garlic"],
            "excluded_warning_tags": ["egg"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert all("eggs" not in match["warning_tags"] for match in body["results"])
    assert "Tomato Egg Rice" not in [
        match["recipe"]["title"] for match in body["results"]
    ]
    assert body["message"] == "Some recipes may be excluded by your restrictions."


def test_match_recipe_filters_multiple_restrictions() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg", "soy sauce", "peas", "rice", "spinach"],
            "excluded_warning_tags": ["egg", "soy", "legumes"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert all(
        not {"eggs", "soy", "legumes"}.intersection(match["warning_tags"])
        for match in body["results"]
    )


def test_match_recipe_filters_normal_recipes_by_warning_tags() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["minced pork", "eggplant", "garlic", "soy sauce"],
            "excluded_warning_tags": ["soy"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert all("soy" not in match["warning_tags"] for match in body["results"])
    assert "Minced Pork with Eggplant" not in [
        match["recipe"]["title"] for match in body["results"]
    ]


def test_match_recipe_rejects_invalid_restriction_value() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["rice"],
            "excluded_warning_tags": ["peanuts"],
        },
    )

    assert response.status_code == 422


def test_match_recipe_returns_clear_message_when_no_suitable_recipe() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg"],
            "excluded_warning_tags": ["egg"],
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "recipe": None,
        "matched_ingredients": [],
        "missing_ingredients": [],
        "match_percentage": 0,
        "warning_tags": [],
        "results": [],
        "message": "No suitable recipe matches the provided ingredients and restrictions.",
    }


def test_match_recipe_normalizes_aliases_before_matching() -> None:
    response = client.post(
        "/recipes/match",
        json={"ingredients": ["eggs", "tomatoes", "rice", "oil"]},
    )

    assert response.status_code == 200
    body = response.json()

    assert body["recipe"]["title"] == "Tomato Egg Rice"
    assert body["matched_ingredients"] == ["egg", "tomato", "rice", "cooking oil"]
    assert body["missing_ingredients"] == []
    assert body["match_percentage"] == 100


def test_match_recipe_returns_combined_warning_tags() -> None:
    response = client.post(
        "/recipes/match",
        json={"ingredients": ["egg", "carrot", "peas", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["recipe"]["title"] == "Fried Rice with Mixed Vegetables"
    assert body["warning_tags"] == ["eggs", "legumes", "soy"]


def test_match_recipe_prefers_fewer_missing_ingredients_when_scores_tie() -> None:
    response = client.post(
        "/recipes/match",
        json={"ingredients": ["egg", "minced pork", "green onion"]},
    )

    assert response.status_code == 200
    body = response.json()
    titles = [match["recipe"]["title"] for match in body["results"]]

    assert titles.index("Tomato Egg Rice") < titles.index(
        "Pork and Cabbage Stir-fry"
    )


def test_match_recipe_filters_by_max_cooking_time() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["minced pork", "eggplant", "garlic", "soy sauce"],
            "max_cooking_time_minutes": 20,
        },
    )

    assert response.status_code == 200
    body = response.json()

    assert all(
        match["recipe"]["cooking_time_minutes"] <= 20 for match in body["results"]
    )
    assert "Minced Pork with Eggplant" not in [
        match["recipe"]["title"] for match in body["results"]
    ]


def test_match_recipe_filters_by_max_missing_ingredients() -> None:
    response = client.post(
        "/recipes/match",
        json={
            "ingredients": ["egg", "tomato", "rice"],
            "max_missing_ingredients": 1,
        },
    )

    assert response.status_code == 200
    body = response.json()

    assert all(len(match["missing_ingredients"]) <= 1 for match in body["results"])


def test_match_recipe_rejects_empty_ingredient_list() -> None:
    response = client.post("/recipes/match", json={"ingredients": []})

    assert response.status_code == 400
    assert response.json() == {"detail": "Please provide at least one ingredient."}


def test_match_recipe_rejects_whitespace_only_ingredients() -> None:
    response = client.post("/recipes/match", json={"ingredients": [" ", "\t"]})

    assert response.status_code == 400
    assert response.json() == {"detail": "Please provide at least one ingredient."}


def test_match_recipe_returns_readable_result_when_no_recipe_matches() -> None:
    response = client.post("/recipes/match", json={"ingredients": ["banana"]})

    assert response.status_code == 200
    assert response.json() == {
        "recipe": None,
        "matched_ingredients": [],
        "missing_ingredients": [],
        "match_percentage": 0,
        "warning_tags": [],
        "results": [],
        "message": "No recipe matches the provided ingredients.",
    }


def test_match_recipe_allows_catalogue_only_ingredients() -> None:
    response = client.post("/recipes/match", json={"ingredients": ["zucchini"]})

    assert response.status_code == 200
    assert response.json()["message"] == "No recipe matches the provided ingredients."


def test_generate_recipe_returns_valid_ollama_recipe(monkeypatch) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return """
        {
          "title": "AI Tomato Egg Bowl",
          "ingredients": ["egg", "tomato", "rice"],
          "missing_ingredients": [],
          "instructions": ["Cook the egg.", "Add tomato.", "Serve over rice."],
          "cooking_time_minutes": 18,
          "warning_tags": ["eggs"],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "AI Tomato Egg Bowl"
    assert body["generated_by_ai"] is True
    assert body["warning_tags"] == ["eggs"]


def test_generate_recipe_prompt_includes_restrictions(monkeypatch) -> None:
    captured_prompt = {}

    def fake_call_ollama(prompt: str) -> str:
        captured_prompt["value"] = prompt
        return """
        {
          "title": "AI Spinach Rice",
          "ingredients": ["spinach", "rice"],
          "missing_ingredients": [],
          "instructions": ["Cook rice.", "Add spinach."],
          "cooking_time_minutes": 15,
          "warning_tags": [],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={
            "ingredients": ["spinach", "rice"],
            "excluded_warning_tags": ["egg", "soy"],
        },
    )

    assert response.status_code == 200
    assert response.json()["title"] == "AI Spinach Rice"
    assert '"eggs"' in captured_prompt["value"]
    assert '"soy"' in captured_prompt["value"]


def test_generate_recipe_prompt_includes_valid_selected_substitutions(
    monkeypatch,
) -> None:
    captured_prompt = {}

    def fake_call_ollama(prompt: str) -> str:
        captured_prompt["value"] = prompt
        return """
        {
          "title": "Soy-Free Rice",
          "ingredients": ["rice", "salt"],
          "missing_ingredients": ["salt"],
          "instructions": ["Cook rice.", "Season with salt."],
          "cooking_time_minutes": 12,
          "warning_tags": [],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={
            "ingredients": ["rice", "soy sauce"],
            "excluded_warning_tags": ["soy"],
            "selected_substitutions": [
                {
                    "original_ingredient": "soya sauce",
                    "alternative_ingredient": "salt",
                }
            ],
        },
    )

    assert response.status_code == 200
    assert '"original_ingredient": "soy sauce"' in captured_prompt["value"]
    assert '"alternative_ingredient": "salt"' in captured_prompt["value"]


def test_generate_recipe_prompt_includes_measurement_system(monkeypatch) -> None:
    captured_prompt = {}

    def fake_call_ollama(prompt: str) -> str:
        captured_prompt["value"] = prompt
        return """
        {
          "title": "AI Rice",
          "ingredients": ["rice"],
          "ingredient_measurements": ["1 cup rice"],
          "missing_ingredients": [],
          "instructions": ["Cook rice."],
          "cooking_time_minutes": 12,
          "warning_tags": [],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["rice"], "measurement_system": "us"},
    )

    assert response.status_code == 200
    assert "Measurement system: us" in captured_prompt["value"]
    assert response.json()["ingredient_measurements"][0]["text"] == "1 cup rice"


def test_generate_recipe_retries_when_generated_recipe_violates_restrictions(
    monkeypatch,
) -> None:
    responses = iter(
        [
            """
            {
              "title": "Egg Draft",
              "ingredients": ["egg", "rice"],
              "missing_ingredients": [],
              "instructions": ["Cook egg with rice."],
              "cooking_time_minutes": 12,
              "warning_tags": [],
              "generated_by_ai": true
            }
            """,
            """
            {
              "title": "Spinach Rice",
              "ingredients": ["spinach", "rice"],
              "missing_ingredients": [],
              "instructions": ["Cook spinach with rice."],
              "cooking_time_minutes": 12,
              "warning_tags": [],
              "generated_by_ai": true
            }
            """,
        ]
    )

    def fake_call_ollama(prompt: str) -> str:
        return next(responses)

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={
            "ingredients": ["spinach", "rice", "egg"],
            "excluded_warning_tags": ["egg"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Spinach Rice"
    assert body["warning_tags"] == []


def test_generate_recipe_fallback_respects_restrictions(monkeypatch) -> None:
    def fake_call_ollama(prompt: str) -> str:
        raise RuntimeError("Ollama is unavailable.")

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={
            "ingredients": ["egg", "tomato", "rice", "spinach"],
            "excluded_warning_tags": ["egg"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["generated_by_ai"] is False
    assert "eggs" not in body["warning_tags"]
    assert body["title"] != "Tomato Egg Rice"


def test_generate_recipe_returns_clear_fallback_when_no_suitable_recipe() -> None:
    response = client.post(
        "/recipes/generate",
        json={
            "ingredients": ["egg"],
            "excluded_warning_tags": ["egg"],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "No recipe found"
    assert body["generated_by_ai"] is False
    assert body["warning_tags"] == []
    assert body["retrieval_fallback_reason"] == (
        "No suitable recipe matches the provided ingredients and restrictions."
    )


def test_generate_recipe_recalculates_when_all_generated_ingredients_are_available(
    monkeypatch,
) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return """
        {
          "title": "Available Pantry Bowl",
          "ingredients": ["egg", "Egg", "tomato", "rice"],
          "missing_ingredients": ["egg", "tomato"],
          "instructions": ["Cook everything together."],
          "cooking_time_minutes": 12,
          "warning_tags": ["tomato", "eggs", "EGGS"],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": [" Egg ", "TOMATO", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["ingredients"] == ["egg", "tomato", "rice"]
    assert body["missing_ingredients"] == []
    assert body["warning_tags"] == ["eggs"]


def test_generate_recipe_normalizes_aliases_before_prompt_and_post_processing(
    monkeypatch,
) -> None:
    def fake_call_ollama(prompt: str) -> str:
        assert '"egg"' in prompt
        assert '"tomato"' in prompt
        assert "eggs" not in json_ingredients_section(prompt)
        return """
        {
          "title": "Alias Pantry Bowl",
          "ingredients": ["eggs", "tomatoes", "rice"],
          "missing_ingredients": [],
          "instructions": ["Cook everything together."],
          "cooking_time_minutes": 12,
          "warning_tags": ["dairy"],
          "generated_by_ai": true
        }
        """

    def json_ingredients_section(prompt: str) -> str:
        return prompt.split("Pantry ingredients:", 1)[1].split("Grounding recipes:", 1)[0]

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["eggs", "tomatoes", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()

    assert body["ingredients"] == ["egg", "tomato", "rice"]
    assert body["missing_ingredients"] == []
    assert body["warning_tags"] == ["eggs"]


def test_generate_recipe_recalculates_one_genuinely_missing_ingredient(
    monkeypatch,
) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return """
        {
          "title": "Tomato Egg Rice Draft",
          "ingredients": ["egg", "tomato", "rice"],
          "missing_ingredients": [],
          "instructions": ["Cook the egg.", "Add tomato.", "Serve with rice."],
          "cooking_time_minutes": 18,
          "warning_tags": [],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["missing_ingredients"] == ["rice"]
    assert body["warning_tags"] == ["eggs"]


def test_generate_recipe_ignores_incorrect_ollama_missing_fields(
    monkeypatch,
) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return """
        {
          "title": "Oil Finish Rice",
          "ingredients": ["egg", "tomato", "rice", "cooking oil"],
          "missing_ingredients": ["egg", "tomato", "rice", "banana"],
          "instructions": ["Cook the pantry ingredients.", "Finish with oil."],
          "cooking_time_minutes": 18,
          "warning_tags": ["eggs"],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["missing_ingredients"] == ["cooking oil"]


def test_generate_recipe_removes_hallucinated_warning_tags(monkeypatch) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return """
        {
          "title": "Rice Tomato Bowl",
          "ingredients": ["rice", "tomato"],
          "missing_ingredients": [],
          "instructions": ["Warm rice.", "Top with tomato."],
          "cooking_time_minutes": 10,
          "warning_tags": ["rice", "tomato", "dairy", "soy"],
          "generated_by_ai": true
        }
        """

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["rice", "tomato"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["warning_tags"] == []


def test_generate_recipe_retries_once_after_invalid_ollama_output(monkeypatch) -> None:
    responses = iter(
        [
            '{"title": "Missing required fields"}',
            """
            {
              "title": "Retry Rice",
              "ingredients": ["rice", "egg"],
              "missing_ingredients": [],
              "instructions": ["Cook rice.", "Top with egg."],
              "cooking_time_minutes": 12,
              "warning_tags": ["eggs"],
              "generated_by_ai": true
            }
            """,
        ]
    )

    def fake_call_ollama(prompt: str) -> str:
        return next(responses)

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Retry Rice"
    assert body["generated_by_ai"] is True


def test_generate_recipe_falls_back_when_ollama_is_unavailable(monkeypatch) -> None:
    def fake_call_ollama(prompt: str) -> str:
        raise RuntimeError("Ollama is unavailable.")

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Tomato Egg Rice"
    assert body["generated_by_ai"] is False
    assert body["warning_tags"] == ["eggs"]


def test_generate_recipe_falls_back_after_invalid_retry(monkeypatch) -> None:
    def fake_call_ollama(prompt: str) -> str:
        return '{"title": "Still invalid"}'

    monkeypatch.setattr(main, "call_ollama", fake_call_ollama)

    response = client.post(
        "/recipes/generate",
        json={"ingredients": ["egg", "tomato", "rice"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Tomato Egg Rice"
    assert body["generated_by_ai"] is False
