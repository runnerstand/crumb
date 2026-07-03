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


def init_db() -> None:
    reset_incompatible_community_posts_table()
    add_missing_community_posts_updated_at_column()
    add_missing_saved_recipes_measurements_column()
    Base.metadata.create_all(bind=engine)
