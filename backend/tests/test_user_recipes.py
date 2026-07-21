from collections.abc import Generator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.database import get_db
from app.main import app


@pytest.fixture()
def client(tmp_path) -> Generator[TestClient]:
    database_url = f"sqlite:///{tmp_path / 'test_smart_pantry.db'}"
    engine = create_engine(
        database_url,
        connect_args={"check_same_thread": False},
    )
    testing_session_local = sessionmaker(
        autocommit=False,
        autoflush=False,
        bind=engine,
    )

    Base.metadata.create_all(bind=engine)

    def override_get_db() -> Generator[Session]:
        db = testing_session_local()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()
    Base.metadata.drop_all(bind=engine)


def recipe_payload() -> dict[str, object]:
    return {
        "title": "Tomato Egg Rice",
        "ingredients": [
            {
                "ingredient_name": "eggs",
                "quantity": "2",
                "unit": "pieces",
            },
            {
                "ingredient_name": "tomato",
                "quantity": "1",
                "unit": "cup",
            },
        ],
        "instructions": ["Cook eggs.", "Serve with tomato and rice."],
        "cooking_time_minutes": 20,
    }


def create_recipe(client: TestClient) -> dict:
    response = client.post("/recipes/user-created", json=recipe_payload())

    assert response.status_code == 201

    return response.json()


def test_create_user_recipe_uses_local_owner_and_catalogue_ingredients(
    client: TestClient,
) -> None:
    response = client.post("/recipes/user-created", json=recipe_payload())

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == 1
    assert body["user_id"] == "local-user"
    assert body["title"] == "Tomato Egg Rice"
    assert body["ingredients"] == [
        {
            "ingredient_id": 1,
            "ingredient_name": "egg",
            "quantity": "2",
            "unit": "pieces",
        },
        {
            "ingredient_id": 2,
            "ingredient_name": "tomato",
            "quantity": "1",
            "unit": "cup",
        },
    ]
    assert body["instructions"] == ["Cook eggs.", "Serve with tomato and rice."]
    assert body["cooking_time_minutes"] == 20
    assert body["servings"] == 2
    assert body["image_url"] is None
    assert "created_at" in body


def test_create_user_recipe_accepts_servings(client: TestClient) -> None:
    payload = recipe_payload()
    payload["servings"] = 4

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 201
    assert response.json()["servings"] == 4


def test_upload_recipe_image_hashes_and_reuses_file(client: TestClient, tmp_path, monkeypatch) -> None:
    from app import recipe_images

    upload_root = tmp_path / "uploads" / "recipes"
    monkeypatch.setattr(recipe_images, "UPLOAD_ROOT", upload_root)
    png_bytes = (
        b"\x89PNG\r\n\x1a\n"
        b"\x00\x00\x00\rIHDR"
        b"\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00"
        b"\x90wS\xde"
    )

    first_response = client.post(
        "/recipes/images",
        files={"image": ("food.txt", png_bytes, "application/octet-stream")},
    )
    second_response = client.post(
        "/recipes/images",
        files={"image": ("food.png", png_bytes, "image/png")},
    )

    assert first_response.status_code == 201
    assert second_response.status_code == 201
    first_body = first_response.json()
    second_body = second_response.json()
    assert first_body == second_body
    assert first_body["content_type"] == "image/png"
    assert first_body["image_url"].startswith("/uploads/recipes/")
    assert first_body["image_url"].endswith(".png")
    assert len(list(upload_root.iterdir())) == 1


def test_upload_recipe_image_rejects_unsupported_content(client: TestClient, tmp_path, monkeypatch) -> None:
    from app import recipe_images

    monkeypatch.setattr(recipe_images, "UPLOAD_ROOT", tmp_path / "uploads" / "recipes")

    response = client.post(
        "/recipes/images",
        files={"image": ("food.jpg", b"not actually an image", "image/jpeg")},
    )

    assert response.status_code == 400
    assert response.json() == {"detail": "Only JPEG, PNG, and WebP images are supported."}


def test_list_user_recipes_returns_newest_first(client: TestClient) -> None:
    client.post("/recipes/user-created", json=recipe_payload())
    second_payload = recipe_payload()
    second_payload["title"] = "Garlic Spinach"
    second_payload["ingredients"] = [
        {"ingredient_name": "spinach", "quantity": "1", "unit": "bunch"}
    ]
    client.post("/recipes/user-created", json=second_payload)

    response = client.get("/recipes/user-created")

    assert response.status_code == 200
    assert [recipe["title"] for recipe in response.json()] == [
        "Garlic Spinach",
        "Tomato Egg Rice",
    ]


def test_read_user_recipe(client: TestClient) -> None:
    recipe = create_recipe(client)

    response = client.get(f"/recipes/user-created/{recipe['id']}")

    assert response.status_code == 200
    assert response.json()["title"] == "Tomato Egg Rice"


def test_update_user_recipe_replaces_ingredients(client: TestClient) -> None:
    recipe = create_recipe(client)
    payload = {
        "title": "Garlic Spinach Bowl",
        "ingredients": [
            {"ingredient_name": "spinach", "quantity": "2", "unit": "cups"},
            {"ingredient_name": "garlic", "quantity": "1", "unit": "clove"},
        ],
        "instructions": ["Stir fry spinach with garlic."],
        "cooking_time_minutes": 10,
        "servings": 3,
    }

    response = client.patch(f"/recipes/user-created/{recipe['id']}", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Garlic Spinach Bowl"
    assert [ingredient["ingredient_name"] for ingredient in body["ingredients"]] == [
        "garlic",
        "spinach",
    ]
    assert body["instructions"] == ["Stir fry spinach with garlic."]
    assert body["cooking_time_minutes"] == 10
    assert body["servings"] == 3


def test_create_user_recipe_rejects_invalid_servings(client: TestClient) -> None:
    payload = recipe_payload()
    payload["servings"] = 0

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_delete_user_recipe(client: TestClient) -> None:
    recipe = create_recipe(client)

    delete_response = client.delete(f"/recipes/user-created/{recipe['id']}")
    list_response = client.get("/recipes/user-created")

    assert delete_response.status_code == 200
    assert delete_response.json() == {"message": "Recipe deleted."}
    assert list_response.json() == []


def test_create_user_recipe_rejects_unsupported_ingredient(client: TestClient) -> None:
    payload = recipe_payload()
    payload["ingredients"] = [{"ingredient_name": "dragonfruit"}]

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 400
    assert response.json() == {"detail": "Unsupported ingredient: dragonfruit"}


def test_create_user_recipe_rejects_blank_title(client: TestClient) -> None:
    payload = recipe_payload()
    payload["title"] = "   "

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_missing_title(client: TestClient) -> None:
    payload = recipe_payload()
    del payload["title"]

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_empty_instructions(client: TestClient) -> None:
    payload = recipe_payload()
    payload["instructions"] = []

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_blank_instruction_step(client: TestClient) -> None:
    payload = recipe_payload()
    payload["instructions"] = ["Cook eggs.", "   "]

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_invalid_cooking_time(client: TestClient) -> None:
    payload = recipe_payload()
    payload["cooking_time_minutes"] = 0

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_no_ingredients(client: TestClient) -> None:
    payload = recipe_payload()
    payload["ingredients"] = []

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_blank_ingredient_name(client: TestClient) -> None:
    payload = recipe_payload()
    payload["ingredients"] = [{"ingredient_name": "   ", "quantity": "1", "unit": "cup"}]

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 422


def test_create_user_recipe_rejects_duplicate_ingredients(client: TestClient) -> None:
    payload = recipe_payload()
    payload["ingredients"] = [
        {"ingredient_name": "egg", "quantity": "1", "unit": "piece"},
        {"ingredient_name": "eggs", "quantity": "2", "unit": "pieces"},
    ]

    response = client.post("/recipes/user-created", json=payload)

    assert response.status_code == 400
    assert response.json() == {"detail": "Duplicate ingredient: egg"}


def test_read_missing_user_recipe_returns_404(client: TestClient) -> None:
    response = client.get("/recipes/user-created/999")

    assert response.status_code == 404
    assert response.json() == {"detail": "Recipe not found."}


def test_update_missing_user_recipe_returns_404(client: TestClient) -> None:
    response = client.patch("/recipes/user-created/999", json=recipe_payload())

    assert response.status_code == 404
    assert response.json() == {"detail": "Recipe not found."}


def test_delete_missing_user_recipe_returns_404(client: TestClient) -> None:
    response = client.delete("/recipes/user-created/999")

    assert response.status_code == 404
    assert response.json() == {"detail": "Recipe not found."}
