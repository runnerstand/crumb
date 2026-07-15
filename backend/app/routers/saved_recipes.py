from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import SavedRecipe
from app.schemas import SavedRecipeCreate
from app.schemas import SavedRecipeRead


router = APIRouter()


@router.post("/recipes/saved", response_model=SavedRecipeRead, status_code=201)
def create_saved_recipe(
    recipe: SavedRecipeCreate,
    db: Session = Depends(get_db),
) -> SavedRecipe:
    saved_recipe = SavedRecipe(
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
    return db.query(SavedRecipe).order_by(SavedRecipe.created_at.desc()).all()


@router.delete("/recipes/saved/{recipe_id}")
def delete_saved_recipe(recipe_id: int, db: Session = Depends(get_db)) -> dict[str, str]:
    saved_recipe = db.get(SavedRecipe, recipe_id)

    if saved_recipe is None:
        raise HTTPException(status_code=404, detail="Saved recipe not found.")

    db.delete(saved_recipe)
    db.commit()

    return {"message": "Saved recipe deleted."}
