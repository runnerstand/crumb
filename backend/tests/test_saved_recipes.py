from collections.abc import Generator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy import inspect
from sqlalchemy import text
from sqlalchemy.orm import Session
from sqlalchemy.orm import sessionmaker

from app import database
from app.database import Base
from app.database import get_db
from app.main import app


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


def test_save_recipe_is_idempotent(client: TestClient) -> None:
    recipe = create_recipe(client)

    first_response = client.post("/recipes/saved", json={"recipe_id": recipe["id"]})
    second_response = client.post("/recipes/saved", json={"recipe_id": recipe["id"]})

    assert first_response.status_code == 201
    assert second_response.status_code == 201
    assert first_response.json()["recipe_id"] == recipe["id"]
    assert second_response.json()["recipe_id"] == recipe["id"]


def test_list_saved_recipes_returns_bookmarked_recipe(client: TestClient) -> None:
    recipe = create_recipe(client)
    client.post("/recipes/saved", json={"recipe_id": recipe["id"]})

    response = client.get("/recipes/saved")

    assert response.status_code == 200
    body = response.json()
    assert len(body) == 1
    assert body[0]["recipe_id"] == recipe["id"]
    assert body[0]["recipe"]["title"] == "Tomato Egg Rice"
    assert body[0]["recipe"]["creator_name"] == "Local User"


def test_read_saved_recipe_status(client: TestClient) -> None:
    recipe = create_recipe(client)

    response = client.get(f"/recipes/saved/{recipe['id']}")

    assert response.status_code == 200
    assert response.json() == {"recipe_id": recipe["id"], "is_saved": False}

    client.post("/recipes/saved", json={"recipe_id": recipe["id"]})

    saved_response = client.get(f"/recipes/saved/{recipe['id']}")
    assert saved_response.json() == {"recipe_id": recipe["id"], "is_saved": True}


def test_delete_saved_recipe_by_recipe_id(client: TestClient) -> None:
    recipe = create_recipe(client)
    client.post("/recipes/saved", json={"recipe_id": recipe["id"]})

    delete_response = client.delete(f"/recipes/saved/{recipe['id']}")

    assert delete_response.status_code == 200
    assert delete_response.json() == {"message": "Saved recipe removed."}
    assert client.get("/recipes/saved").json() == []


def test_delete_recipe_removes_saved_bookmark(client: TestClient) -> None:
    recipe = create_recipe(client)
    client.post("/recipes/saved", json={"recipe_id": recipe["id"]})

    delete_response = client.delete(f"/recipes/user-created/{recipe['id']}")

    assert delete_response.status_code == 200
    assert client.get("/recipes/saved").json() == []


def test_saved_recipe_status_for_missing_recipe_is_false(client: TestClient) -> None:
    response = client.get("/recipes/saved/999")

    assert response.status_code == 200
    assert response.json() == {"recipe_id": 999, "is_saved": False}


def test_legacy_sqlite_saved_recipes_schema_allows_bookmark_insert(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    engine = create_engine(
        f"sqlite:///{tmp_path / 'legacy_saved_recipes.db'}",
        connect_args={"check_same_thread": False},
    )
    with engine.begin() as connection:
        connection.execute(
            text(
                """
                CREATE TABLE saved_recipes (
                    id INTEGER NOT NULL PRIMARY KEY,
                    title VARCHAR NOT NULL,
                    ingredients JSON NOT NULL,
                    ingredient_measurements JSON NOT NULL,
                    missing_ingredients JSON NOT NULL,
                    instructions JSON NOT NULL,
                    cooking_time_minutes INTEGER NOT NULL,
                    created_at DATETIME NOT NULL
                )
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO saved_recipes (
                    id,
                    title,
                    ingredients,
                    ingredient_measurements,
                    missing_ingredients,
                    instructions,
                    cooking_time_minutes,
                    created_at
                )
                VALUES (
                    1,
                    'Legacy Saved Recipe',
                    '["egg"]',
                    '[]',
                    '[]',
                    '["Cook it."]',
                    10,
                    CURRENT_TIMESTAMP
                )
                """
            )
        )

    monkeypatch.setattr(database, "engine", engine)
    database.init_db()
    database.init_db()

    columns_by_name = {
        column["name"]: column for column in inspect(engine).get_columns("saved_recipes")
    }
    assert columns_by_name["title"]["nullable"] is True
    assert columns_by_name["ingredient_measurements"]["nullable"] is True
    assert columns_by_name["cooking_time_minutes"]["nullable"] is True
    with engine.connect() as connection:
        legacy_count = connection.execute(text("SELECT COUNT(*) FROM saved_recipes")).scalar_one()
    assert legacy_count == 1

    testing_session_local = sessionmaker(
        autocommit=False,
        autoflush=False,
        bind=engine,
    )

    def override_get_db() -> Generator[Session]:
        db = testing_session_local()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    try:
        with TestClient(app) as test_client:
            recipe = create_recipe(test_client)
            response = test_client.post("/recipes/saved", json={"recipe_id": recipe["id"]})
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 201
    assert response.json()["recipe_id"] == recipe["id"]


def test_legacy_sqlite_recipes_schema_gets_default_servings(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    engine = create_engine(
        f"sqlite:///{tmp_path / 'legacy_recipes.db'}",
        connect_args={"check_same_thread": False},
    )
    with engine.begin() as connection:
        connection.execute(
            text(
                """
                CREATE TABLE recipes (
                    id INTEGER NOT NULL PRIMARY KEY,
                    user_id VARCHAR(255) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    instructions JSON NOT NULL,
                    cooking_time_minutes INTEGER,
                    created_at DATETIME NOT NULL
                )
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO recipes (
                    id,
                    user_id,
                    title,
                    instructions,
                    cooking_time_minutes,
                    created_at
                )
                VALUES (
                    1,
                    'local-user',
                    'Legacy Recipe',
                    '["Cook it."]',
                    10,
                    CURRENT_TIMESTAMP
                )
                """
            )
        )

    monkeypatch.setattr(database, "engine", engine)
    database.init_db()
    database.init_db()

    columns_by_name = {
        column["name"]: column for column in inspect(engine).get_columns("recipes")
    }
    assert columns_by_name["servings"]["nullable"] is False
    with engine.connect() as connection:
        legacy_servings = connection.execute(
            text("SELECT servings FROM recipes WHERE id = 1")
        ).scalar_one()
    assert legacy_servings == 2
