from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import LOCAL_USER_ID
from app.models import Recipe
from app.models import SavedRecipe
from app.schemas import SavedRecipeCreate
from app.schemas import SavedRecipeRead


router = APIRouter()


@router.post("/recipes/saved", response_model=SavedRecipeRead, status_code=201)
def create_saved_recipe(
    recipe: SavedRecipeCreate,
    db: Session = Depends(get_db),
) -> SavedRecipe:
    if recipe.recipe_id is not None:
        source_recipe = db.get(Recipe, recipe.recipe_id)

        if source_recipe is None:
            raise HTTPException(status_code=404, detail="Recipe not found.")

        existing_saved_recipe = (
            db.query(SavedRecipe)
            .filter(
                SavedRecipe.user_id == LOCAL_USER_ID,
                SavedRecipe.recipe_id == recipe.recipe_id,
            )
            .first()
        )

        if existing_saved_recipe is not None:
            raise HTTPException(status_code=409, detail="Recipe already saved.")

    saved_recipe = SavedRecipe(
        user_id=LOCAL_USER_ID,
        recipe_id=recipe.recipe_id,
        title=recipe.title,
        ingredients=recipe.ingredients,
        ingredient_measurements=recipe.ingredient_measurements,
        missing_ingredients=recipe.missing_ingredients,
        instructions=recipe.instructions,
        cooking_time_minutes=recipe.cooking_time_minutes,
    )

    db.add(saved_recipe)
    db.commit()
    db.refresh(saved_recipe)

    return saved_recipe


@router.get("/recipes/saved", response_model=list[SavedRecipeRead])
def list_saved_recipes(db: Session = Depends(get_db)) -> list[SavedRecipe]:
    return (
        db.query(SavedRecipe)
        .filter(SavedRecipe.user_id == LOCAL_USER_ID)
        .order_by(SavedRecipe.created_at.desc())
        .all()
    )


@router.delete("/recipes/saved/{recipe_id}")
def delete_saved_recipe(recipe_id: int, db: Session = Depends(get_db)) -> dict[str, str]:
    saved_recipe = db.get(SavedRecipe, recipe_id)

    if saved_recipe is None:
        raise HTTPException(status_code=404, detail="Saved recipe not found.")

    if saved_recipe.user_id != LOCAL_USER_ID:
        raise HTTPException(
            status_code=403,
            detail="Only the recipe saver can delete this saved recipe.",
        )

    db.delete(saved_recipe)
    db.commit()

    return {"message": "Saved recipe deleted."}
