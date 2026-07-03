import os
from collections.abc import Generator
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import inspect
from sqlalchemy import text
from sqlalchemy import create_engine
from sqlalchemy.engine import Connection
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.orm import Session
from sqlalchemy.orm import sessionmaker


PROJECT_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(PROJECT_ROOT / ".env")

DATABASE_URL = os.environ.get("DATABASE_URL") or os.environ.get(
    "SMART_PANTRY_DATABASE_URL"
)

if not DATABASE_URL:
    raise RuntimeError(
        "DATABASE_URL is not configured. Set it in backend/.env for normal "
        "runtime, or set SMART_PANTRY_DATABASE_URL for isolated tests."
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

    missing_columns = sorted(expected_column_names - column_names)
    raise RuntimeError(
        "Existing community_posts table is missing required columns: "
        f"{', '.join(missing_columns)}. Refusing to drop existing data."
    )


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


def table_names() -> set[str]:
    return set(inspect(engine).get_table_names())


def column_names(table_name: str) -> set[str]:
    return {
        column["name"] for column in inspect(engine).get_columns(table_name)
    }


def index_names(table_name: str) -> set[str]:
    return {index["name"] for index in inspect(engine).get_indexes(table_name)}


def unique_constraint_names(table_name: str) -> set[str | None]:
    return {
        constraint["name"]
        for constraint in inspect(engine).get_unique_constraints(table_name)
    }


def foreign_keys(table_name: str) -> list[dict]:
    return inspect(engine).get_foreign_keys(table_name)


def has_foreign_key(
    table_name: str,
    constrained_columns: list[str],
    referred_table: str,
    referred_columns: list[str],
    ondelete: str | None = None,
) -> bool:
    for foreign_key in foreign_keys(table_name):
        if foreign_key["constrained_columns"] != constrained_columns:
            continue
        if foreign_key["referred_table"] != referred_table:
            continue
        if foreign_key["referred_columns"] != referred_columns:
            continue
        if ondelete is None:
            return True
        if foreign_key.get("options", {}).get("ondelete") == ondelete:
            return True

    return False


def add_column_if_missing(
    connection: Connection,
    table_name: str,
    column_name: str,
    definition: str,
) -> None:
    if column_name in column_names(table_name):
        return

    connection.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {definition}"))


def add_index_if_missing(
    connection: Connection,
    table_name: str,
    index_name: str,
    columns: str,
) -> None:
    if index_name in index_names(table_name):
        return

    connection.execute(text(f"CREATE INDEX {index_name} ON {table_name} ({columns})"))


def add_unique_constraint_if_missing(
    connection: Connection,
    table_name: str,
    constraint_name: str,
    columns: str,
) -> None:
    if constraint_name in unique_constraint_names(table_name):
        return

    connection.execute(
        text(f"ALTER TABLE {table_name} ADD CONSTRAINT {constraint_name} UNIQUE ({columns})")
    )


def migrate_sqlite_schema() -> None:
    existing_tables = table_names()

    with engine.begin() as connection:
        if "saved_recipes" in existing_tables:
            add_column_if_missing(
                connection,
                "saved_recipes",
                "user_id",
                "user_id VARCHAR(255) NOT NULL DEFAULT 'local-user'",
            )
            add_column_if_missing(
                connection,
                "saved_recipes",
                "recipe_id",
                "recipe_id INTEGER",
            )

        if "community_posts" in existing_tables:
            add_column_if_missing(
                connection,
                "community_posts",
                "recipe_id",
                "recipe_id INTEGER",
            )


def seed_local_users(connection: Connection) -> None:
    connection.execute(
        text(
            "INSERT IGNORE INTO users (id, display_name, created_at) "
            "VALUES ('local-user', 'Local User', CURRENT_TIMESTAMP)"
        )
    )
    connection.execute(
        text(
            "INSERT IGNORE INTO users (id, display_name, created_at) "
            "SELECT DISTINCT creator_id, creator_name, CURRENT_TIMESTAMP "
            "FROM community_posts"
        )
    )
    connection.execute(
        text(
            "INSERT IGNORE INTO users (id, display_name, created_at) "
            "SELECT DISTINCT creator_id, creator_name, CURRENT_TIMESTAMP "
            "FROM community_comments"
        )
    )


def migrate_mysql_erd_schema() -> None:
    if engine.dialect.name != "mysql":
        migrate_sqlite_schema()
        return

    existing_tables = table_names()

    with engine.begin() as connection:
        seed_local_users(connection)

        if "saved_recipes" in existing_tables:
            add_column_if_missing(
                connection,
                "saved_recipes",
                "user_id",
                "user_id VARCHAR(255) NULL",
            )
            add_column_if_missing(
                connection,
                "saved_recipes",
                "recipe_id",
                "recipe_id INTEGER NULL",
            )
            connection.execute(
                text("UPDATE saved_recipes SET user_id = 'local-user' WHERE user_id IS NULL")
            )
            connection.execute(
                text("ALTER TABLE saved_recipes MODIFY user_id VARCHAR(255) NOT NULL")
            )
            add_index_if_missing(
                connection,
                "saved_recipes",
                "ix_saved_recipes_user_id",
                "user_id",
            )
            add_index_if_missing(
                connection,
                "saved_recipes",
                "ix_saved_recipes_recipe_id",
                "recipe_id",
            )
            add_unique_constraint_if_missing(
                connection,
                "saved_recipes",
                "uq_saved_recipe_user_recipe",
                "user_id, recipe_id",
            )

        if "community_posts" in existing_tables:
            add_column_if_missing(
                connection,
                "community_posts",
                "recipe_id",
                "recipe_id INTEGER NULL",
            )
            add_index_if_missing(
                connection,
                "community_posts",
                "ix_community_posts_recipe_id",
                "recipe_id",
            )

        if "ingredients" in existing_tables:
            add_column_if_missing(
                connection,
                "ingredients",
                "aliases",
                "aliases JSON NULL",
            )
            add_column_if_missing(
                connection,
                "ingredients",
                "warning_tags",
                "warning_tags JSON NULL",
            )
            add_column_if_missing(
                connection,
                "ingredients",
                "needs_review",
                "needs_review BOOL NULL",
            )
            connection.execute(
                text("UPDATE ingredients SET aliases = JSON_ARRAY() WHERE aliases IS NULL")
            )
            connection.execute(
                text(
                    "UPDATE ingredients SET warning_tags = JSON_ARRAY() "
                    "WHERE warning_tags IS NULL"
                )
            )
            connection.execute(
                text(
                    "UPDATE ingredients SET needs_review = FALSE "
                    "WHERE needs_review IS NULL"
                )
            )
            connection.execute(text("ALTER TABLE ingredients MODIFY aliases JSON NOT NULL"))
            connection.execute(
                text("ALTER TABLE ingredients MODIFY warning_tags JSON NOT NULL")
            )
            connection.execute(
                text("ALTER TABLE ingredients MODIFY needs_review BOOL NOT NULL")
            )

        add_mysql_foreign_keys(connection)


def drop_foreign_key_if_exists(
    connection: Connection,
    table_name: str,
    constrained_columns: list[str],
    referred_table: str,
) -> None:
    for foreign_key in foreign_keys(table_name):
        if foreign_key["constrained_columns"] != constrained_columns:
            continue
        if foreign_key["referred_table"] != referred_table:
            continue

        connection.execute(
            text(f"ALTER TABLE {table_name} DROP FOREIGN KEY {foreign_key['name']}")
        )


def add_mysql_foreign_keys(connection: Connection) -> None:
    if not has_foreign_key("recipes", ["user_id"], "users", ["id"]):
        connection.execute(
            text(
                "ALTER TABLE recipes "
                "ADD CONSTRAINT fk_recipes_user_id "
                "FOREIGN KEY (user_id) REFERENCES users(id)"
            )
        )

    if not has_foreign_key("saved_recipes", ["user_id"], "users", ["id"]):
        connection.execute(
            text(
                "ALTER TABLE saved_recipes "
                "ADD CONSTRAINT fk_saved_recipes_user_id "
                "FOREIGN KEY (user_id) REFERENCES users(id)"
            )
        )

    if not has_foreign_key(
        "saved_recipes",
        ["recipe_id"],
        "recipes",
        ["id"],
        "SET NULL",
    ):
        connection.execute(
            text(
                "ALTER TABLE saved_recipes "
                "ADD CONSTRAINT fk_saved_recipes_recipe_id "
                "FOREIGN KEY (recipe_id) REFERENCES recipes(id) "
                "ON DELETE SET NULL"
            )
        )

    if not has_foreign_key("community_posts", ["creator_id"], "users", ["id"]):
        connection.execute(
            text(
                "ALTER TABLE community_posts "
                "ADD CONSTRAINT fk_community_posts_creator_id "
                "FOREIGN KEY (creator_id) REFERENCES users(id)"
            )
        )

    if not has_foreign_key(
        "community_posts",
        ["recipe_id"],
        "recipes",
        ["id"],
        "SET NULL",
    ):
        connection.execute(
            text(
                "ALTER TABLE community_posts "
                "ADD CONSTRAINT fk_community_posts_recipe_id "
                "FOREIGN KEY (recipe_id) REFERENCES recipes(id) "
                "ON DELETE SET NULL"
            )
        )

    if not has_foreign_key("community_comments", ["creator_id"], "users", ["id"]):
        connection.execute(
            text(
                "ALTER TABLE community_comments "
                "ADD CONSTRAINT fk_community_comments_creator_id "
                "FOREIGN KEY (creator_id) REFERENCES users(id)"
            )
        )

    if not has_foreign_key(
        "community_comments",
        ["post_id"],
        "community_posts",
        ["id"],
        "CASCADE",
    ):
        drop_foreign_key_if_exists(
            connection,
            "community_comments",
            ["post_id"],
            "community_posts",
        )
        connection.execute(
            text(
                "ALTER TABLE community_comments "
                "ADD CONSTRAINT fk_community_comments_post_id "
                "FOREIGN KEY (post_id) REFERENCES community_posts(id) "
                "ON DELETE CASCADE"
            )
        )


def init_db() -> None:
    reset_incompatible_community_posts_table()
    add_missing_community_posts_updated_at_column()
    add_missing_saved_recipes_measurements_column()
    Base.metadata.create_all(bind=engine)
    migrate_mysql_erd_schema()
