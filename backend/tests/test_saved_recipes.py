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


def saved_recipe_payload() -> dict[str, object]:
    return {
        "title": "Tomato Egg Rice",
        "ingredients": ["egg", "tomato", "rice", "cooking oil"],
        "missing_ingredients": ["cooking oil"],
        "instructions": [
            "Cut the tomatoes.",
            "Cook the eggs in a pan.",
            "Serve with rice.",
        ],
        "cooking_time_minutes": 20,
    }


def test_create_saved_recipe(client: TestClient) -> None:
    response = client.post("/recipes/saved", json=saved_recipe_payload())

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == 1
    assert body["title"] == "Tomato Egg Rice"
    assert body["ingredients"] == ["egg", "tomato", "rice", "cooking oil"]
    assert body["missing_ingredients"] == ["cooking oil"]
    assert body["instructions"] == [
        "Cut the tomatoes.",
        "Cook the eggs in a pan.",
        "Serve with rice.",
    ]
    assert body["cooking_time_minutes"] == 20
    assert "created_at" in body


def test_list_saved_recipes(client: TestClient) -> None:
    client.post("/recipes/saved", json=saved_recipe_payload())

    response = client.get("/recipes/saved")

    assert response.status_code == 200
    body = response.json()
    assert len(body) == 1
    assert body[0]["title"] == "Tomato Egg Rice"


def test_delete_saved_recipe(client: TestClient) -> None:
    create_response = client.post("/recipes/saved", json=saved_recipe_payload())
    recipe_id = create_response.json()["id"]

    delete_response = client.delete(f"/recipes/saved/{recipe_id}")
    list_response = client.get("/recipes/saved")

    assert delete_response.status_code == 200
    assert delete_response.json() == {"message": "Saved recipe deleted."}
    assert list_response.json() == []


def test_delete_missing_saved_recipe_returns_404(client: TestClient) -> None:
    response = client.delete("/recipes/saved/999")

    assert response.status_code == 404
    assert response.json() == {"detail": "Saved recipe not found."}
