from datetime import datetime
from datetime import timezone

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models import LOCAL_USER_ID
from app.models import RecipeRating
from app.schemas import RecipeRatingRead


def get_recipe_rating_summary(
    db: Session,
    recipe_id: int,
    user_id: str = LOCAL_USER_ID,
) -> RecipeRatingRead:
    average_rating, rating_count = (
        db.query(func.avg(RecipeRating.rating), func.count(RecipeRating.id))
        .filter(RecipeRating.recipe_id == recipe_id)
        .one()
    )
    user_rating = (
        db.query(RecipeRating.rating)
        .filter(RecipeRating.recipe_id == recipe_id)
        .filter(RecipeRating.user_id == user_id)
        .scalar()
    )

    return RecipeRatingRead(
        recipe_id=recipe_id,
        average_rating=float(average_rating) if average_rating is not None else None,
        rating_count=int(rating_count or 0),
        user_rating=int(user_rating) if user_rating is not None else None,
    )


def upsert_recipe_rating(
    db: Session,
    recipe_id: int,
    rating: int,
    user_id: str = LOCAL_USER_ID,
) -> RecipeRating:
    recipe_rating = (
        db.query(RecipeRating)
        .filter(RecipeRating.recipe_id == recipe_id)
        .filter(RecipeRating.user_id == user_id)
        .first()
    )

    if recipe_rating is None:
        recipe_rating = RecipeRating(
            recipe_id=recipe_id,
            user_id=user_id,
            rating=rating,
        )
        db.add(recipe_rating)
    else:
        recipe_rating.rating = rating
        recipe_rating.updated_at = datetime.now(timezone.utc)

    return recipe_rating
