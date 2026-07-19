from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.models import LOCAL_USER_ID
from app.models import Recipe
from app.models import RecipeIngredient
from app.models import SavedRecipe
from app.models import User
from app.schemas import SavedRecipeCreate
from app.schemas import SavedRecipeRead


router = APIRouter()


def ensure_local_user(db: Session) -> None:
    if db.get(User, LOCAL_USER_ID) is not None:
        return

    db.add(User(id=LOCAL_USER_ID, display_name="Local User"))
    db.flush()


def get_recipe_or_404(recipe_id: int, db: Session) -> Recipe:
    recipe = (
        db.query(Recipe)
        .options(selectinload(Recipe.ingredients).selectinload(RecipeIngredient.ingredient))
        .options(selectinload(Recipe.creator))
        .filter(Recipe.id == recipe_id)
        .first()
    )

    if recipe is None:
        raise HTTPException(status_code=404, detail="Recipe not found.")

    return recipe


def load_saved_recipe(recipe_id: int, db: Session) -> SavedRecipe | None:
    return (
        db.query(SavedRecipe)
        .join(SavedRecipe.recipe)
        .options(
            selectinload(SavedRecipe.recipe)
            .selectinload(Recipe.ingredients)
            .selectinload(RecipeIngredient.ingredient)
        )
        .options(selectinload(SavedRecipe.recipe).selectinload(Recipe.creator))
        .filter(
            SavedRecipe.user_id == LOCAL_USER_ID,
            SavedRecipe.recipe_id == recipe_id,
        )
        .first()
    )


@router.post("/recipes/saved", response_model=SavedRecipeRead, status_code=201)
def create_saved_recipe(
    recipe: SavedRecipeCreate,
    db: Session = Depends(get_db),
) -> SavedRecipe:
    ensure_local_user(db)
    saved_recipe = load_saved_recipe(recipe.recipe_id, db)
    if saved_recipe is not None:
        return saved_recipe

    linked_recipe = get_recipe_or_404(recipe.recipe_id, db)
    saved_recipe = SavedRecipe(
        user_id=LOCAL_USER_ID,
        recipe_id=linked_recipe.id,
    )

    db.add(saved_recipe)
    db.commit()
    db.refresh(saved_recipe)

    return load_saved_recipe(linked_recipe.id, db) or saved_recipe


@router.get("/recipes/saved", response_model=list[SavedRecipeRead])
def list_saved_recipes(db: Session = Depends(get_db)) -> list[SavedRecipe]:
    ensure_local_user(db)
    saved_recipes = (
        db.query(SavedRecipe)
        .join(SavedRecipe.recipe)
        .options(
            selectinload(SavedRecipe.recipe)
            .selectinload(Recipe.ingredients)
            .selectinload(RecipeIngredient.ingredient)
        )
        .options(selectinload(SavedRecipe.recipe).selectinload(Recipe.creator))
        .filter(
            SavedRecipe.user_id == LOCAL_USER_ID,
            SavedRecipe.recipe_id.isnot(None),
        )
        .order_by(SavedRecipe.created_at.desc())
        .all()
    )

    return saved_recipes


@router.get("/recipes/saved/{recipe_id}")
def read_saved_recipe_status(
    recipe_id: int,
    db: Session = Depends(get_db),
) -> dict[str, object]:
    ensure_local_user(db)
    return {"recipe_id": recipe_id, "is_saved": load_saved_recipe(recipe_id, db) is not None}


@router.delete("/recipes/saved/{recipe_id}")
def delete_saved_recipe(recipe_id: int, db: Session = Depends(get_db)) -> dict[str, str]:
    ensure_local_user(db)
    db.query(SavedRecipe).filter(
        SavedRecipe.user_id == LOCAL_USER_ID,
        SavedRecipe.recipe_id == recipe_id,
    ).delete()
    db.commit()

    return {"message": "Saved recipe removed."}
