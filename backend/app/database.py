import os
from collections.abc import Generator
from pathlib import Path

from sqlalchemy import inspect
from sqlalchemy import text
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.orm import Session
from sqlalchemy.orm import sessionmaker


DATABASE_FILE = Path(__file__).resolve().parents[1] / "smart_pantry.db"
LOCAL_ENV_FILE = Path(__file__).resolve().parents[1] / ".env.local"


def load_local_environment(path: Path = LOCAL_ENV_FILE) -> None:
    if not path.exists():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


load_local_environment()

DATABASE_URL = os.environ.get(
    "SMART_PANTRY_DATABASE_URL",
    f"sqlite:///{DATABASE_FILE}",
)

if DATABASE_URL.startswith("sqlite"):
    engine = create_engine(
        DATABASE_URL,
        connect_args={"check_same_thread": False},
    )
else:
    engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db() -> Generator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def reset_incompatible_community_posts_table() -> None:
    if engine.dialect.name != "sqlite":
        return

    inspector = inspect(engine)

    if "community_posts" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("community_posts")
    }
    expected_column_names = {
        "id",
        "creator_id",
        "creator_name",
        "title",
        "ingredients_json",
        "caption",
        "created_at",
    }

    if expected_column_names.issubset(column_names):
        return

    with engine.begin() as connection:
        connection.execute(text("DROP TABLE community_posts"))


def add_missing_community_posts_updated_at_column() -> None:
    inspector = inspect(engine)

    if "community_posts" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("community_posts")
    }

    if "updated_at" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE community_posts ADD COLUMN updated_at DATETIME"))
        connection.execute(
            text(
                "UPDATE community_posts "
                "SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP)"
            )
        )


def add_missing_community_posts_recipe_id_column() -> None:
    inspector = inspect(engine)

    if "community_posts" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("community_posts")
    }

    if "recipe_id" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE community_posts ADD COLUMN recipe_id INTEGER"))


def add_missing_saved_recipes_measurements_column() -> None:
    inspector = inspect(engine)

    if "saved_recipes" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("saved_recipes")
    }

    if "ingredient_measurements" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE saved_recipes ADD COLUMN ingredient_measurements JSON"))
        connection.execute(
            text("UPDATE saved_recipes SET ingredient_measurements = '[]'")
        )


def add_missing_saved_recipes_user_id_column() -> None:
    inspector = inspect(engine)

    if "saved_recipes" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("saved_recipes")
    }

    if "user_id" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE saved_recipes ADD COLUMN user_id TEXT"))
        connection.execute(
            text("UPDATE saved_recipes SET user_id = :local_user_id"),
            {"local_user_id": "local-user"},
        )


def add_missing_saved_recipes_recipe_id_column() -> None:
    inspector = inspect(engine)

    if "saved_recipes" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("saved_recipes")
    }

    if "recipe_id" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE saved_recipes ADD COLUMN recipe_id INTEGER"))


def add_missing_recipes_image_url_column() -> None:
    inspector = inspect(engine)

    if "recipes" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("recipes")
    }

    if "image_url" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE recipes ADD COLUMN image_url VARCHAR(500)"))


def add_missing_recipes_servings_column() -> None:
    inspector = inspect(engine)

    if "recipes" not in inspector.get_table_names():
        return

    column_names = {
        column["name"] for column in inspector.get_columns("recipes")
    }

    if "servings" in column_names:
        return

    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE recipes ADD COLUMN servings INTEGER NOT NULL DEFAULT 2"))


def make_mysql_saved_recipe_snapshot_columns_nullable() -> None:
    if engine.dialect.name != "mysql":
        return

    inspector = inspect(engine)

    if "saved_recipes" not in inspector.get_table_names():
        return

    mysql_nullable_snapshot_columns = {
        "title": "VARCHAR(255)",
        "ingredients": "JSON",
        "ingredient_measurements": "JSON",
        "missing_ingredients": "JSON",
        "instructions": "JSON",
        "cooking_time_minutes": "INTEGER",
    }
    columns_by_name = {
        column["name"]: column
        for column in inspector.get_columns("saved_recipes")
    }
    columns_to_alter = [
        (column_name, column_type)
        for column_name, column_type in mysql_nullable_snapshot_columns.items()
        if column_name in columns_by_name
        and columns_by_name[column_name].get("nullable") is False
    ]

    if not columns_to_alter:
        return

    with engine.begin() as connection:
        for column_name, column_type in columns_to_alter:
            connection.execute(
                text(f"ALTER TABLE saved_recipes MODIFY COLUMN {column_name} {column_type} NULL")
            )


def rebuild_legacy_saved_recipes_table_for_bookmarks() -> None:
    if engine.dialect.name != "sqlite":
        return

    inspector = inspect(engine)
    table_names = inspector.get_table_names()

    if "saved_recipes_legacy" in table_names:
        with engine.begin() as connection:
            connection.execute(text("DROP TABLE IF EXISTS saved_recipes"))
            connection.execute(
                text("ALTER TABLE saved_recipes_legacy RENAME TO saved_recipes")
            )
        inspector = inspect(engine)
        table_names = inspector.get_table_names()

    if "saved_recipes" not in table_names:
        return

    columns = inspector.get_columns("saved_recipes")
    columns_by_name = {column["name"]: column for column in columns}
    legacy_snapshot_columns = {
        "title",
        "ingredients",
        "ingredient_measurements",
        "missing_ingredients",
        "instructions",
        "cooking_time_minutes",
    }
    needs_rebuild = any(
        columns_by_name.get(column_name, {}).get("nullable") is False
        for column_name in legacy_snapshot_columns
    )

    if not needs_rebuild:
        return

    with engine.begin() as connection:
        connection.execute(text("DROP INDEX IF EXISTS ix_saved_recipes_id"))
        connection.execute(text("DROP INDEX IF EXISTS ix_saved_recipes_user_id"))
        connection.execute(text("DROP INDEX IF EXISTS ix_saved_recipes_recipe_id"))
        connection.execute(text("ALTER TABLE saved_recipes RENAME TO saved_recipes_legacy"))
        connection.execute(
            text(
                """
                CREATE TABLE saved_recipes (
                    id INTEGER NOT NULL,
                    user_id VARCHAR(255) NOT NULL,
                    recipe_id INTEGER,
                    title VARCHAR,
                    ingredients JSON,
                    ingredient_measurements JSON,
                    missing_ingredients JSON,
                    instructions JSON,
                    cooking_time_minutes INTEGER,
                    created_at DATETIME NOT NULL,
                    PRIMARY KEY (id),
                    CONSTRAINT uq_saved_recipe_user_recipe UNIQUE (user_id, recipe_id),
                    FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE,
                    FOREIGN KEY(recipe_id) REFERENCES recipes (id) ON DELETE CASCADE
                )
                """
            )
        )
        connection.execute(
            text("CREATE INDEX IF NOT EXISTS ix_saved_recipes_id ON saved_recipes (id)")
        )
        connection.execute(
            text(
                "CREATE INDEX IF NOT EXISTS ix_saved_recipes_user_id "
                "ON saved_recipes (user_id)"
            )
        )
        connection.execute(
            text(
                "CREATE INDEX IF NOT EXISTS ix_saved_recipes_recipe_id "
                "ON saved_recipes (recipe_id)"
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO saved_recipes (
                    id,
                    user_id,
                    recipe_id,
                    title,
                    ingredients,
                    ingredient_measurements,
                    missing_ingredients,
                    instructions,
                    cooking_time_minutes,
                    created_at
                )
                SELECT
                    id,
                    COALESCE(user_id, :local_user_id),
                    recipe_id,
                    title,
                    ingredients,
                    ingredient_measurements,
                    missing_ingredients,
                    instructions,
                    cooking_time_minutes,
                    COALESCE(created_at, CURRENT_TIMESTAMP)
                FROM saved_recipes_legacy
                """
            ),
            {"local_user_id": "local-user"},
        )
        connection.execute(text("DROP TABLE saved_recipes_legacy"))


def init_db() -> None:
    reset_incompatible_community_posts_table()
    add_missing_community_posts_updated_at_column()
    add_missing_community_posts_recipe_id_column()
    add_missing_saved_recipes_measurements_column()
    add_missing_saved_recipes_user_id_column()
    add_missing_saved_recipes_recipe_id_column()
    add_missing_recipes_servings_column()
    add_missing_recipes_image_url_column()
    make_mysql_saved_recipe_snapshot_columns_nullable()
    rebuild_legacy_saved_recipes_table_for_bookmarks()
    Base.metadata.create_all(bind=engine)
