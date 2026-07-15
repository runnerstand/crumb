from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_suggestions_return_empty_list_for_short_query() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "e"})

    assert response.status_code == 200
    assert response.json() == []


def test_suggestions_prefer_prefix_matches_then_partial_matches() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "mi"})

    assert response.status_code == 200
    assert response.json() == [
        "minced beef",
        "minced lamb",
        "minced pork",
        "cumin",
    ]


def test_suggestions_are_case_insensitive() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "TO"})

    assert response.status_code == 200
    assert response.json() == ["tomato"]


def test_suggestions_exclude_duplicates() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "co"})

    assert response.status_code == 200
    body = response.json()
    assert body.count("cooking oil") == 1
    assert len(body) == len(set(body))


def test_suggestions_return_at_most_five_items() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "i"})

    assert response.status_code == 200
    assert response.json() == []

    response = client.get("/ingredients/suggestions", params={"q": "in"})

    assert response.status_code == 200
    assert len(response.json()) <= 5


def test_ingredient_categories_include_warning_tags() -> None:
    response = client.get("/ingredients/categories")

    assert response.status_code == 200
    body = response.json()
    categories_by_name = {category["name"]: category for category in body}

    assert categories_by_name["egg"]["tags"] == ["eggs"]
    assert categories_by_name["egg"]["category"] == "protein"
    assert categories_by_name["soy sauce"]["tags"] == ["soy"]
    assert categories_by_name["peas"]["tags"] == ["legumes"]
    assert categories_by_name["tomato"]["tags"] == []


def test_suggestions_use_aliases() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "sc"})

    assert response.status_code == 200
    assert response.json() == ["green onion"]


def test_suggestions_include_catalogue_only_ingredients() -> None:
    response = client.get("/ingredients/suggestions", params={"q": "zu"})

    assert response.status_code == 200
    assert response.json() == ["zucchini"]


def test_categories_include_catalogue_only_ingredients() -> None:
    response = client.get("/ingredients/categories")

    assert response.status_code == 200
    categories_by_name = {category["name"]: category for category in response.json()}

    assert categories_by_name["zucchini"]["category"] == "vegetable"
    assert categories_by_name["zucchini"]["tags"] == []


def test_substitutions_return_valid_alternatives() -> None:
    response = client.get("/ingredients/soy sauce/substitutions")

    assert response.status_code == 200
    body = response.json()
    assert body[0] == {
        "original_ingredient": "soy sauce",
        "alternative_ingredient": "salt",
        "alternative_measurement": {
            "original_text": "1 teaspoon salt",
            "text": "5 millilitres salt",
            "original_quantity": 1.0,
            "original_unit": "teaspoons",
            "converted_quantity": 5.0,
            "converted_unit": "millilitres",
        },
        "note": "Use a smaller amount and adjust to taste.",
        "warning_tags": [],
        "dietary_tags": ["soy-free"],
    }


def test_substitutions_normalize_aliases() -> None:
    response = client.get("/ingredients/eggs/substitutions")

    assert response.status_code == 200
    body = response.json()
    assert [substitution["alternative_ingredient"] for substitution in body] == [
        "tofu",
        "chickpea flour batter",
    ]


def test_substitutions_convert_measurements_to_selected_system() -> None:
    response = client.get(
        "/ingredients/egg/substitutions",
        params={"measurement_system": "us"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body[0]["alternative_measurement"]["text"] == "3.5 ounces tofu"


def test_substitutions_filter_restricted_alternatives() -> None:
    response = client.get(
        "/ingredients/egg/substitutions",
        params={"excluded_warning_tags": "soy"},
    )

    assert response.status_code == 200
    body = response.json()
    assert [substitution["alternative_ingredient"] for substitution in body] == [
        "chickpea flour batter",
    ]


def test_substitutions_return_empty_for_unknown_ingredient() -> None:
    response = client.get("/ingredients/dragonfruit/substitutions")

    assert response.status_code == 200
    assert response.json() == []


def test_substitutions_return_empty_when_no_suitable_alternative() -> None:
    response = client.get(
        "/ingredients/egg/substitutions",
        params=[
            ("excluded_warning_tags", "soy"),
            ("excluded_warning_tags", "legumes"),
        ],
    )

    assert response.status_code == 200
    assert response.json() == []
