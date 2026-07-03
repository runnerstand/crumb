import argparse
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError

from app.database import SessionLocal
from app.database import init_db
from app.ingredient_catalog import INGREDIENTS_FILE
from app.ingredient_catalog import Ingredient as CatalogueIngredient
from app.ingredient_catalog import load_ingredients
from app.ingredient_catalog import normalize_ingredient
from app.models import Ingredient


@dataclass
class ImportStats:
    total_records: int = 0
    inserted_rows: int = 0
    updated_rows: int = 0
    skipped_rows: int = 0
    duplicate_rows: int = 0
    invalid_records: list[str] | None = None

    def __post_init__(self) -> None:
        if self.invalid_records is None:
            self.invalid_records = []


def raw_record_count(path: Path) -> int:
    import json

    with path.open(encoding="utf-8") as ingredient_file:
        payload = json.load(ingredient_file)

    if not isinstance(payload, list):
        raise ValueError("Ingredient data must be a list.")

    return len(payload)


def validate_catalogue_records(path: Path) -> list[str]:
    import json

    with path.open(encoding="utf-8") as ingredient_file:
        payload = json.load(ingredient_file)

    if not isinstance(payload, list):
        raise ValueError("Ingredient data must be a list.")

    invalid_records: list[str] = []

    for index, raw_ingredient in enumerate(payload):
        if not isinstance(raw_ingredient, dict):
            invalid_records.append(f"record {index}: expected object")
            continue

        name = normalize_ingredient(str(raw_ingredient.get("name", "")))
        if not name:
            invalid_records.append(f"record {index}: missing valid name")
            continue

        aliases = raw_ingredient.get("aliases", [])
        if not isinstance(aliases, list):
            invalid_records.append(f"record {index} ({name}): aliases must be a list")

        warning_tags = raw_ingredient.get("warning_tags", [])
        if not isinstance(warning_tags, list):
            invalid_records.append(
                f"record {index} ({name}): warning_tags must be a list"
            )

    return invalid_records


def ingredient_values(ingredient: CatalogueIngredient) -> dict[str, Any]:
    return {
        "name": ingredient.name,
        "category": ingredient.category,
        "aliases": list(ingredient.aliases),
        "warning_tags": list(ingredient.warning_tags),
        "needs_review": ingredient.needs_review,
    }


def import_ingredients(path: Path = INGREDIENTS_FILE) -> ImportStats:
    stats = ImportStats(total_records=raw_record_count(path))
    stats.invalid_records = validate_catalogue_records(path)

    catalogue = load_ingredients(path)
    ingredients_by_name: dict[str, CatalogueIngredient] = {}

    for ingredient in catalogue:
        if ingredient.name in ingredients_by_name:
            stats.duplicate_rows += 1
            stats.skipped_rows += 1
            continue

        ingredients_by_name[ingredient.name] = ingredient

    init_db()

    db = SessionLocal()
    try:
        existing_ingredients = {
            ingredient.name: ingredient
            for ingredient in db.scalars(select(Ingredient)).all()
        }

        for ingredient in ingredients_by_name.values():
            values = ingredient_values(ingredient)
            existing = existing_ingredients.get(ingredient.name)

            if existing is None:
                db.add(Ingredient(**values))
                stats.inserted_rows += 1
                continue

            changed = False
            for field_name, value in values.items():
                if getattr(existing, field_name) != value:
                    setattr(existing, field_name, value)
                    changed = True

            if changed:
                stats.updated_rows += 1

        db.commit()
    except SQLAlchemyError:
        db.rollback()
        raise
    finally:
        db.close()

    return stats


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Import the JSON ingredient catalogue into the runtime database."
    )
    parser.add_argument(
        "--path",
        type=Path,
        default=INGREDIENTS_FILE,
        help="Path to ingredients.json.",
    )
    args = parser.parse_args()

    stats = import_ingredients(args.path)

    print(f"total_json_records={stats.total_records}")
    print(f"inserted_rows={stats.inserted_rows}")
    print(f"updated_rows={stats.updated_rows}")
    print(f"skipped_rows={stats.skipped_rows}")
    print(f"duplicate_rows={stats.duplicate_rows}")

    if stats.invalid_records:
        print("invalid_records:")
        for invalid_record in stats.invalid_records:
            print(f"- {invalid_record}")


if __name__ == "__main__":
    main()
